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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PrimeEarnCallbackService {

    private static final String TRANSACTION_PREFIX = "primeearn:";

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Value("${primeearn.postback-salt:}")
    private String postbackSalt;

    @Value("${primeearn.callback-url:}")
    private String callbackUrl;

    public PrimeEarnCallbackService(
            ClaimRepository claimRepository,
            UserRepository userRepository,
            UserStatsRepository userStatsRepository) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.userStatsRepository = userStatsRepository;
    }

    @Transactional
    public void process(String rawQuery) {
        requireConfiguration();
        ParsedQuery query = parseQuery(rawQuery);
        verifyHash(query);

        int type = parseType(requiredParameter(query.parameters(), "type"));
        if (type < 1 || type > 4) {
            throw badRequest("Unsupported Prime Earn survey event type");
        }

        long userId = parseUserId(requiredParameter(query.parameters(), "user"));
        String transactionId = requiredTransactionId(query.parameters(), "tx_id");
        int reward = parseReward(requiredParameter(query.parameters(), "reward"));

        if (type == 4) {
            if (reward >= 0) {
                throw badRequest("Prime Earn reconciliation rewards must be negative");
            }
        } else if (reward < 0) {
            throw badRequest("Prime Earn survey rewards must not be negative");
        } else if (reward == 0) {
            return;
        }

        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prime Earn user not found"));
        String externalTransactionId = TRANSACTION_PREFIX + transactionId;
        var existingTransaction = claimRepository.findByExternalTransactionId(externalTransactionId);
        if (existingTransaction.isPresent()) {
            requireSameUser(existingTransaction.get(), userId);
            return;
        }

        if (type == 4) {
            reverseReward(user, userId, transactionId, requiredTransactionId(
                    query.parameters(), "reconciled_tx_id"), reward);
        } else {
            creditReward(user, reward, externalTransactionId);
        }
    }

    private void creditReward(User user, int reward, String externalTransactionId) {
        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.addExact(stats.getAnnualPoints(), reward));
            stats.setMonthlyPoints(Math.addExact(stats.getMonthlyPoints(), reward));
            stats.setNumberOfClaims(Math.addExact(stats.getNumberOfClaims(), 1));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Prime Earn reward exceeds the coin limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(reward)
                .externalTransactionId(externalTransactionId)
                .build());
        userStatsRepository.save(stats);
    }

    private void reverseReward(
            User user,
            long userId,
            String transactionId,
            String reconciledTransactionId,
            int reward) {
        String originalTransactionId = TRANSACTION_PREFIX + reconciledTransactionId;
        Claim original = claimRepository.findByExternalTransactionId(originalTransactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Original Prime Earn transaction was not found"));
        requireSameUser(original, userId);
        if (original.getAmount() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Original Prime Earn transaction is invalid");
        }

        int reversedPoints;
        try {
            reversedPoints = Math.negateExact(reward);
            if (BigDecimal.valueOf(original.getAmount()).intValueExact() != reversedPoints) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Prime Earn reconciliation does not match the credited reward");
            }
        } catch (ArithmeticException exception) {
            throw badRequest("Prime Earn reconciliation reward exceeds the coin limit");
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.subtractExact(stats.getAnnualPoints(), reversedPoints));
            stats.setMonthlyPoints(Math.subtractExact(stats.getMonthlyPoints(), reversedPoints));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Prime Earn reconciliation exceeds the coin limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(reward)
                .externalTransactionId(TRANSACTION_PREFIX + transactionId)
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
                    HttpStatus.CONFLICT, "Prime Earn transaction ID belongs to another user");
        }
    }

    private void requireConfiguration() {
        if (postbackSalt == null || postbackSalt.isBlank()
                || callbackUrl == null || callbackUrl.isBlank()
                || !callbackUrl.startsWith("https://")
                || callbackUrl.contains("?") || callbackUrl.contains("#")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Prime Earn callback configuration is incomplete");
        }
    }

    private ParsedQuery parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw badRequest("Prime Earn callback query is missing");
        }

        Map<String, String> parameters = new HashMap<>();
        StringBuilder unsignedQuery = new StringBuilder();
        try {
            for (String part : rawQuery.split("&", -1)) {
                int separator = part.indexOf('=');
                if (separator <= 0) {
                    throw badRequest("Invalid Prime Earn callback parameter");
                }
                String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
                String value = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
                if (parameters.putIfAbsent(key, value) != null) {
                    throw badRequest("Duplicate Prime Earn callback parameter");
                }
                if (!key.equals("hash") && !key.equals("shash")) {
                    if (!unsignedQuery.isEmpty()) {
                        unsignedQuery.append('&');
                    }
                    unsignedQuery.append(part);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid encoding in Prime Earn callback query");
        }

        return new ParsedQuery(Map.copyOf(parameters), unsignedQuery.toString());
    }

    private void verifyHash(ParsedQuery query) {
        String suppliedHash = query.parameters().get("hash");
        if (suppliedHash == null || !suppliedHash.matches("(?i)[0-9a-f]{40}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Prime Earn callback hash is missing or invalid");
        }

        String unsignedUrl = callbackUrl + "?" + query.unsignedQuery();
        String expectedHash = createHash(unsignedUrl + postbackSalt);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                suppliedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Prime Earn callback hash is invalid");
        }
    }

    private String createHash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to verify Prime Earn callback signature", exception);
        }
    }

    private int parseType(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid Prime Earn survey event type");
        }
    }

    private long parseUserId(String value) {
        try {
            long userId = Long.parseLong(value);
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid Prime Earn user ID");
        }
        throw badRequest("Invalid Prime Earn user ID");
    }

    private int parseReward(String value) {
        try {
            return new BigDecimal(value).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw badRequest("Prime Earn reward must be a whole number of coins");
        }
    }

    private String requiredTransactionId(Map<String, String> parameters, String name) {
        String value = requiredParameter(parameters, name);
        if (value.length() > 245) {
            throw badRequest("Prime Earn transaction ID is too long");
        }
        return value;
    }

    private String requiredParameter(Map<String, String> parameters, String name) {
        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            throw badRequest("Prime Earn callback parameter is missing: " + name);
        }
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ParsedQuery(Map<String, String> parameters, String unsignedQuery) {
    }
}
