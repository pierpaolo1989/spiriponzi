package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TheoremReachCallbackService {

    private static final String TRANSACTION_PREFIX = "theoremreach:";
    private static final String REVERSAL_PREFIX = "theoremreach:reversal:";
    private static final int MAX_TRANSACTION_ID_LENGTH =
            255 - Math.max(TRANSACTION_PREFIX.length(), REVERSAL_PREFIX.length());

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Value("${theoremreach.api-key:}")
    private String apiKey;

    @Value("${theoremreach.secret-key:}")
    private String secretKey;

    @Value("${theoremreach.callback-url:}")
    private String callbackUrl;

    public TheoremReachCallbackService(
            ClaimRepository claimRepository,
            UserRepository userRepository,
            UserStatsRepository userStatsRepository) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.userStatsRepository = userStatsRepository;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    public String createIframeUrl(String userId) {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TheoremReach surveys are not configured");
        }
        String validatedUserId = Long.toString(parseUserId(userId));
        return UriComponentsBuilder.fromUriString("https://theoremreach.com/respondent_entry/direct")
                .queryParam("api_key", apiKey)
                .queryParam("user_id", validatedUserId)
                .queryParam("transaction_id", UUID.randomUUID())
                .build()
                .encode()
                .toUriString();
    }

    @Transactional
    public void process(String rawQuery) {
        requireCallbackConfiguration();
        ParsedPayload payload = parsePayload(rawQuery);
        verifyHash(payload);

        Map<String, String> parameters = payload.parameters();
        if ("true".equalsIgnoreCase(parameters.get("debug"))) {
            return;
        }

        long userId = parseUserId(requiredParameter(parameters, "user_id"));
        String transactionId = requiredTransactionId(parameters);
        int reward = parseReward(requiredParameter(parameters, "reward"));

        if (isReversal(parameters.get("reversal"))) {
            if (reward == 0) {
                throw badRequest("TheoremReach reversal reward must not be zero");
            }
            reverseReward(userId, transactionId, reward);
            return;
        }
        if (reward < 0) {
            throw badRequest("TheoremReach reward cannot be negative without a reversal");
        }
        if (reward == 0) {
            return;
        }
        creditReward(userId, transactionId, reward);
    }

    private void creditReward(long userId, String transactionId, int reward) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TheoremReach user not found"));
        String externalTransactionId = TRANSACTION_PREFIX + transactionId;
        var existingTransaction = claimRepository.findByExternalTransactionId(externalTransactionId);
        if (existingTransaction.isPresent()) {
            requireSameUser(existingTransaction.get(), userId);
            return;
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.addExact(stats.getAnnualPoints(), reward));
            stats.setMonthlyPoints(Math.addExact(stats.getMonthlyPoints(), reward));
            stats.setNumberOfClaims(Math.addExact(stats.getNumberOfClaims(), 1));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "TheoremReach reward exceeds the points limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(reward)
                .externalTransactionId(externalTransactionId)
                .build());
        userStatsRepository.save(stats);
    }

    private void reverseReward(long userId, String transactionId, int reward) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TheoremReach user not found"));
        String externalTransactionId = TRANSACTION_PREFIX + transactionId;
        String reversalTransactionId = REVERSAL_PREFIX + transactionId;
        var existingReversal = claimRepository.findByExternalTransactionId(reversalTransactionId);
        if (existingReversal.isPresent()) {
            requireSameUser(existingReversal.get(), userId);
            return;
        }

        Claim original = claimRepository.findByExternalTransactionId(externalTransactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Original TheoremReach transaction was not found"));
        requireSameUser(original, userId);
        if (original.getAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Original TheoremReach transaction is invalid");
        }

        int reversedPoints;
        try {
            reversedPoints = Math.toIntExact(Math.abs((long) reward));
            if (BigDecimal.valueOf(original.getAmount()).intValueExact() != reversedPoints) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "TheoremReach reversal does not match the credited reward");
            }
        } catch (ArithmeticException exception) {
            throw badRequest("TheoremReach reversal reward exceeds the points limit");
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.subtractExact(stats.getAnnualPoints(), reversedPoints));
            stats.setMonthlyPoints(Math.subtractExact(stats.getMonthlyPoints(), reversedPoints));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "TheoremReach reversal exceeds the points limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(-reversedPoints)
                .externalTransactionId(reversalTransactionId)
                .build());
        userStatsRepository.save(stats);
    }

    private UserStats createUserStats(User user) {
        UserStats stats = new UserStats();
        stats.setUser(user);
        user.setUserStats(stats);
        userStatsRepository.save(stats);
        userRepository.save(user);
        return stats;
    }

    private void requireSameUser(Claim claim, long userId) {
        if (!claim.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "TheoremReach transaction ID belongs to another user");
        }
    }

    private void requireCallbackConfiguration() {
        if (secretKey == null || secretKey.isBlank()
                || callbackUrl == null || callbackUrl.isBlank()
                || !callbackUrl.startsWith("https://")
                || callbackUrl.contains("?") || callbackUrl.contains("#")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TheoremReach callback configuration is incomplete");
        }
    }

    private ParsedPayload parsePayload(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank() || rawQuery.length() > 8192) {
            throw badRequest("TheoremReach callback payload is missing or too long");
        }

        String[] parts = rawQuery.split("&", -1);
        Map<String, String> parameters = new HashMap<>();
        StringBuilder unsignedQuery = new StringBuilder();
        String suppliedHash = null;
        try {
            for (int index = 0; index < parts.length; index++) {
                String part = parts[index];
                int separator = part.indexOf('=');
                if (separator <= 0) {
                    throw badRequest("Invalid TheoremReach callback parameter");
                }

                String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
                String value = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
                if (parameters.putIfAbsent(key, value) != null) {
                    throw badRequest("Duplicate TheoremReach callback parameter");
                }

                if (key.equals("hash")) {
                    if (index != parts.length - 1) {
                        throw badRequest("TheoremReach callback hash must be the final parameter");
                    }
                    suppliedHash = value;
                } else {
                    if (!unsignedQuery.isEmpty()) {
                        unsignedQuery.append('&');
                    }
                    unsignedQuery.append(part);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid encoding in TheoremReach callback");
        }

        if (suppliedHash == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TheoremReach callback hash is missing");
        }
        return new ParsedPayload(Map.copyOf(parameters), unsignedQuery.toString(), suppliedHash);
    }

    private void verifyHash(ParsedPayload payload) {
        if (!payload.suppliedHash().matches("[A-Za-z0-9_-]{27}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TheoremReach callback hash is invalid");
        }

        String unsignedUrl = callbackUrl + "?" + payload.unsignedQuery();
        String expectedHash = createHash(unsignedUrl);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                payload.suppliedHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TheoremReach callback hash is invalid");
        }
    }

    private String createHash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to verify TheoremReach callback signature", exception);
        }
    }

    private boolean isReversal(String reversal) {
        return "true".equalsIgnoreCase(reversal) || "1".equals(reversal);
    }

    private long parseUserId(String value) {
        try {
            long userId = Long.parseLong(value);
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid TheoremReach user ID");
        }
        throw badRequest("Invalid TheoremReach user ID");
    }

    private int parseReward(String value) {
        try {
            return new BigDecimal(value).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw badRequest("TheoremReach reward must be a whole number of points");
        }
    }

    private String requiredTransactionId(Map<String, String> parameters) {
        String transactionId = requiredParameter(parameters, "tx_id");
        if (transactionId.length() > MAX_TRANSACTION_ID_LENGTH) {
            throw badRequest("TheoremReach transaction ID is too long");
        }
        return transactionId;
    }

    private String requiredParameter(Map<String, String> parameters, String name) {
        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            throw badRequest("TheoremReach callback parameter is missing: " + name);
        }
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ParsedPayload(Map<String, String> parameters, String unsignedQuery, String suppliedHash) {
    }
}
