package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BitLabsCallbackService {

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Value("${bitlabs.secret:}")
    private String secret;

    @Value("${bitlabs.callback-url:}")
    private String callbackUrl;

    public BitLabsCallbackService(
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
        Map<String, String> parameters = parseQuery(rawQuery);
        verifyHash(rawQuery, parameters.get("hash"));

        String activityType = requiredParameter(parameters, "activity_type").toUpperCase(Locale.ROOT);
        if (!activityType.equals("COMPLETE")
                && !activityType.equals("START_BONUS")
                && !activityType.equals("RECONCILIATION")) {
            if (activityType.equals("SCREENOUT")) {
                return;
            }
            throw badRequest("Unsupported BitLabs activity type");
        }

        long userId = parseUserId(parameters.get("uid"));
        String transactionId = requiredTransactionId(parameters, "tx");
        BigDecimal value = parseReward(parameters.get("val"));
        if (value.signum() == 0) {
            return;
        }
        if (!activityType.equals("RECONCILIATION") && value.signum() < 0) {
            throw badRequest("Reward callbacks must not have a negative value");
        }

        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BitLabs user not found"));

        var existingTransaction = claimRepository.findByExternalTransactionId(transactionId);
        if (existingTransaction.isPresent()) {
            Claim existing = existingTransaction.get();
            if (!existing.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction ID belongs to another user");
            }
            return;
        }

        if (activityType.equals("RECONCILIATION")) {
            String referenceId = requiredTransactionId(parameters, "ref");
            Claim original = claimRepository.findByExternalTransactionId(referenceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "Original BitLabs transaction was not found"));
            if (!original.getUser().getId().equals(userId) || original.getAmount() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Reconciliation does not match an original reward");
            }
        }

        int points;
        try {
            points = value.intValueExact();
        } catch (ArithmeticException exception) {
            throw badRequest("BitLabs reward values must be whole coins");
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.addExact(stats.getAnnualPoints(), points));
            stats.setMonthlyPoints(Math.addExact(stats.getMonthlyPoints(), points));
            if (activityType.equals("COMPLETE") || activityType.equals("START_BONUS")) {
                stats.setNumberOfClaims(Math.addExact(stats.getNumberOfClaims(), 1));
            }
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "BitLabs reward exceeds the coin limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(value.doubleValue())
                .externalTransactionId(transactionId)
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

    private void requireConfiguration() {
        if (secret == null || secret.isBlank() || callbackUrl == null || callbackUrl.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "BitLabs callback credentials are not configured");
        }
        if (!callbackUrl.startsWith("https://") || callbackUrl.contains("?") || callbackUrl.contains("#")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "BITLABS_CALLBACK_URL must be an HTTPS URL without a query");
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw badRequest("BitLabs callback query is missing");
        }

        Map<String, String> parameters = new HashMap<>();
        try {
            for (String part : rawQuery.split("&", -1)) {
                int separator = part.indexOf('=');
                if (separator <= 0) {
                    throw badRequest("Invalid BitLabs callback parameter");
                }
                String key = decode(part.substring(0, separator)).toLowerCase(Locale.ROOT);
                String value = decode(part.substring(separator + 1));
                if (parameters.putIfAbsent(key, value) != null) {
                    throw badRequest("Duplicate BitLabs callback parameter");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid encoding in BitLabs callback query");
        }
        return parameters;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void verifyHash(String rawQuery, String suppliedHash) {
        int hashStart = rawQuery.lastIndexOf("&hash=");
        if (hashStart < 0 || suppliedHash == null || hashStart + 6 >= rawQuery.length()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "BitLabs callback hash is missing");
        }
        String rawHash = rawQuery.substring(hashStart + 6);
        if (rawHash.contains("&") || !rawHash.matches("[0-9a-f]{40}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "BitLabs callback hash is invalid");
        }
        String signedUrl = callbackUrl + "?" + rawQuery.substring(0, hashStart);
        String expectedHash = createHash(signedUrl);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                rawHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "BitLabs callback hash is invalid");
        }
    }

    private String createHash(String signedUrl) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(signedUrl.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to verify BitLabs callback signature", exception);
        }
    }

    private long parseUserId(String value) {
        try {
            long userId = Long.parseLong(value);
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid BitLabs user ID");
        }
        throw badRequest("Invalid BitLabs user ID");
    }

    private BigDecimal parseReward(String value) {
        try {
            return new BigDecimal(requiredValue(value, "val"));
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid BitLabs reward value");
        }
    }

    private String requiredParameter(Map<String, String> parameters, String name) {
        return requiredValue(parameters.get(name), name);
    }

    private String requiredTransactionId(Map<String, String> parameters, String name) {
        String value = requiredParameter(parameters, name);
        if (value.length() > 255) {
            throw badRequest("BitLabs transaction ID is too long");
        }
        return value;
    }

    private String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw badRequest("BitLabs callback parameter is missing: " + name);
        }
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

}
