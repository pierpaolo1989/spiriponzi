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
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CherriesCallbackService {

    private static final String TRANSACTION_PREFIX = "cherries:";
    private static final String REVERSAL_PREFIX = "cherries:reversal:";
    private static final int MAX_TRANSACTION_ID_LENGTH = 237;

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Value("${cherries.supplier-id:}")
    private String supplierId;

    @Value("${cherries.hash-key:}")
    private String hashKey;

    @Value("${cherries.callback-url:}")
    private String callbackUrl;

    public CherriesCallbackService(
            ClaimRepository claimRepository,
            UserRepository userRepository,
            UserStatsRepository userStatsRepository) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.userStatsRepository = userStatsRepository;
    }

    public boolean isConfigured() {
        return supplierId != null && !supplierId.isBlank()
                && hashKey != null && !hashKey.isBlank();
    }

    public String createIframeUrl(String userId) {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Cherries surveys are not configured");
        }
        String panelistId = Long.toString(parseUserId(userId));
        return UriComponentsBuilder.fromUriString("https://grabcherries.com/wall")
                .queryParam("pid", panelistId)
                .queryParam("sid", supplierId)
                .build()
                .encode()
                .toUriString();
    }

    @Transactional
    public void process(String rawPayload) {
        requireCallbackConfiguration();
        ParsedPayload payload = parsePayload(rawPayload);
        verifyHash(payload);

        String status = requiredParameter(payload.parameters(), "status");
        String subcode = requiredParameter(payload.parameters(), "subcode");
        String transactionId = requiredTransactionId(payload.parameters());

        if (isReversal(status, subcode)) {
            long userId = parseUserId(requiredParameter(payload.parameters(), "pid"));
            int reward = parseReward(requiredParameter(payload.parameters(), "user_reward"));
            if (reward >= 0) {
                throw badRequest("Cherries reversal rewards must be negative");
            }
            reverseReward(userId, transactionId, reward);
            return;
        }

        if (!status.equals("100") || !(subcode.equals("100") || subcode.equals("101"))) {
            return;
        }

        long userId = parseUserId(requiredParameter(payload.parameters(), "pid"));
        int reward = parseReward(requiredParameter(payload.parameters(), "user_reward"));
        if (reward <= 0) {
            throw badRequest("Cherries completion rewards must be positive whole points");
        }
        creditReward(userId, transactionId, reward);
    }

    private void creditReward(long userId, String transactionId, int reward) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cherries user not found"));
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
                    HttpStatus.UNPROCESSABLE_ENTITY, "Cherries reward exceeds the points limit");
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cherries user not found"));
        String externalTransactionId = TRANSACTION_PREFIX + transactionId;
        String reversalTransactionId = REVERSAL_PREFIX + transactionId;
        var existingReversal = claimRepository.findByExternalTransactionId(reversalTransactionId);
        if (existingReversal.isPresent()) {
            requireSameUser(existingReversal.get(), userId);
            return;
        }

        Claim original = claimRepository.findByExternalTransactionId(externalTransactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Original Cherries transaction was not found"));
        requireSameUser(original, userId);
        if (original.getAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Original Cherries transaction is invalid");
        }

        int reversedPoints;
        try {
            reversedPoints = Math.negateExact(reward);
            if (BigDecimal.valueOf(original.getAmount()).intValueExact() != reversedPoints) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Cherries reversal does not match the credited reward");
            }
        } catch (ArithmeticException exception) {
            throw badRequest("Cherries reversal reward exceeds the points limit");
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.subtractExact(stats.getAnnualPoints(), reversedPoints));
            stats.setMonthlyPoints(Math.subtractExact(stats.getMonthlyPoints(), reversedPoints));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Cherries reversal exceeds the points limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(reward)
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
                    HttpStatus.CONFLICT, "Cherries transaction ID belongs to another user");
        }
    }

    private void requireCallbackConfiguration() {
        if (hashKey == null || hashKey.isBlank()
                || callbackUrl == null || callbackUrl.isBlank()
                || !callbackUrl.startsWith("https://")
                || callbackUrl.contains("?") || callbackUrl.contains("#")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Cherries callback configuration is incomplete");
        }
    }

    private ParsedPayload parsePayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank() || rawPayload.length() > 8192) {
            throw badRequest("Cherries callback payload is missing or too long");
        }

        String[] parts = rawPayload.split("&", -1);
        Map<String, String> parameters = new HashMap<>();
        StringBuilder unsignedQuery = new StringBuilder();
        String suppliedHash = null;
        try {
            for (int index = 0; index < parts.length; index++) {
                String part = parts[index];
                int separator = part.indexOf('=');
                if (separator <= 0) {
                    throw badRequest("Invalid Cherries callback parameter");
                }

                String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8)
                        .toLowerCase(java.util.Locale.ROOT);
                String value = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
                if (parameters.putIfAbsent(key, value) != null) {
                    throw badRequest("Duplicate Cherries callback parameter");
                }

                if (key.equals("hash")) {
                    if (index != parts.length - 1) {
                        throw badRequest("Cherries callback hash must be the final parameter");
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
            throw badRequest("Invalid encoding in Cherries callback");
        }

        if (suppliedHash == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cherries callback hash is missing");
        }
        return new ParsedPayload(Map.copyOf(parameters), unsignedQuery.toString(), suppliedHash);
    }

    private void verifyHash(ParsedPayload payload) {
        if (!payload.suppliedHash().matches("[A-Za-z0-9_-]{43}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cherries callback hash is invalid");
        }

        String unsignedUrl = callbackUrl + "?" + payload.unsignedQuery();
        String expectedHash = createHash(unsignedUrl);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                payload.suppliedHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cherries callback hash is invalid");
        }
    }

    private String createHash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to verify Cherries callback signature", exception);
        }
    }

    private boolean isReversal(String status, String subcode) {
        return subcode.equals("301") || subcode.equals("401")
                || status.equals("301") || status.equals("401");
    }

    private long parseUserId(String value) {
        try {
            long userId = Long.parseLong(value);
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid Cherries panelist ID");
        }
        throw badRequest("Invalid Cherries panelist ID");
    }

    private int parseReward(String value) {
        try {
            return new BigDecimal(value).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw badRequest("Cherries reward must be a whole number of points");
        }
    }

    private String requiredTransactionId(Map<String, String> parameters) {
        String transactionId = requiredParameter(parameters, "transaction_id");
        if (transactionId.length() > MAX_TRANSACTION_ID_LENGTH) {
            throw badRequest("Cherries transaction ID is too long");
        }
        return transactionId;
    }

    private String requiredParameter(Map<String, String> parameters, String name) {
        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            throw badRequest("Cherries callback parameter is missing: " + name);
        }
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ParsedPayload(Map<String, String> parameters, String unsignedQuery, String suppliedHash) {
    }
}
