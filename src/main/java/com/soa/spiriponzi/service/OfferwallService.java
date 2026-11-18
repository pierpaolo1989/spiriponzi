package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OfferwallService {

    private static final String TRANSACTION_PREFIX = "offerwall:";
    private static final String REVERSAL_PREFIX = "offerwall:reversal:";

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Value("${offerwall.public-key:}")
    private String publicKey;

    @Value("${offerwall.secret-key:}")
    private String secretKey;

    @Value("${offerwall.require-signature:false}")
    private boolean requireSignature;

    public OfferwallService(
            ClaimRepository claimRepository,
            UserRepository userRepository,
            UserStatsRepository userStatsRepository) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.userStatsRepository = userStatsRepository;
    }

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && requireSignature;
    }

    public String createSignedWallUrl(String userId) {
        requireWallConfiguration();
        long parsedUserId = parseUserId(userId);
        String normalizedUserId = Long.toString(parsedUserId);
        String signature = hmacSha256("appId=" + publicKey + "&userId=" + normalizedUserId);
        return UriComponentsBuilder.fromUriString("https://offerwall.gg/wall/{publicKey}")
                .queryParam("userId", normalizedUserId)
                .queryParam("signature", signature)
                .buildAndExpand(publicKey)
                .encode()
                .toUriString();
    }

    @Transactional
    public void processCallback(
            String userIdValue,
            String transactionId,
            String currencyAmount,
            String statusValue,
            String testValue,
            String suppliedSignature) {
        requireCallbackConfiguration();
        requiredValue(userIdValue, "userId");
        requiredValue(transactionId, "transactionId");
        requiredValue(currencyAmount, "currencyAmount");
        requiredValue(statusValue, "status");
        if (transactionId.length() > 230 || currencyAmount.length() > 32) {
            throw badRequest("Offerwall callback parameter is too long");
        }
        verifyCallbackSignature(userIdValue, transactionId, currencyAmount, suppliedSignature);

        String test = testValue == null || testValue.isBlank() ? "0" : testValue;
        if (test.equals("1")) {
            return;
        }
        if (!test.equals("0")) {
            throw badRequest("Invalid Offerwall test flag");
        }

        String status = statusValue.toLowerCase(Locale.ROOT);
        if (!status.equals("credited") && !status.equals("reversed")) {
            throw badRequest("Unsupported Offerwall callback status");
        }

        long userId = parseUserId(userIdValue);
        BigDecimal amount = parseAmount(currencyAmount);
        if ((status.equals("credited") && amount.signum() <= 0)
                || (status.equals("reversed") && amount.signum() >= 0)) {
            throw badRequest("Offerwall amount does not match callback status");
        }

        String originalTransactionId = TRANSACTION_PREFIX + transactionId;
        String reversalTransactionId = REVERSAL_PREFIX + transactionId;
        if (status.equals("credited")) {
            creditConversion(userId, originalTransactionId, amount);
        } else {
            reverseConversion(userId, originalTransactionId, reversalTransactionId, amount);
        }
    }

    private void creditConversion(long userId, String externalTransactionId, BigDecimal amount) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offerwall user not found"));

        var existingTransaction = claimRepository.findByExternalTransactionId(externalTransactionId);
        if (existingTransaction.isPresent()) {
            requireSameUser(existingTransaction.get(), userId);
            return;
        }

        int points = wholePoints(amount);
        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.addExact(stats.getAnnualPoints(), points));
            stats.setMonthlyPoints(Math.addExact(stats.getMonthlyPoints(), points));
            stats.setNumberOfClaims(Math.addExact(stats.getNumberOfClaims(), 1));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Offerwall reward exceeds the coin limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(points)
                .externalTransactionId(externalTransactionId)
                .build());
        userStatsRepository.save(stats);
    }

    private void reverseConversion(
            long userId,
            String originalTransactionId,
            String reversalTransactionId,
            BigDecimal reversalAmount) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offerwall user not found"));

        var existingReversal = claimRepository.findByExternalTransactionId(reversalTransactionId);
        if (existingReversal.isPresent()) {
            requireSameUser(existingReversal.get(), userId);
            return;
        }

        Claim original = claimRepository.findByExternalTransactionId(originalTransactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Original Offerwall conversion was not found"));
        requireSameUser(original, userId);
        if (original.getAmount() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Original Offerwall conversion is invalid");
        }

        int points = storedPoints(original.getAmount());
        if (wholePoints(reversalAmount.abs()) != points) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Offerwall reversal does not match the credited reward");
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.subtractExact(stats.getAnnualPoints(), points));
            stats.setMonthlyPoints(Math.subtractExact(stats.getMonthlyPoints(), points));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Offerwall reversal exceeds the coin limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(-points)
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

    private void requireCallbackConfiguration() {
        if (secretKey == null || secretKey.isBlank()
                || publicKey == null || publicKey.isBlank()
                || !requireSignature) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Offerwall callback configuration is incomplete");
        }
    }

    private void requireWallConfiguration() {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Offerwall integration is not configured");
        }
    }

    private void verifyCallbackSignature(
            String userId, String transactionId, String amount, String suppliedSignature) {
        if (suppliedSignature == null || !suppliedSignature.matches("(?i)[0-9a-f]{64}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Offerwall signature is missing or invalid");
        }
        String expected = hmacSha256(userId + ":" + transactionId + ":" + amount);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                suppliedSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Offerwall signature is invalid");
        }
    }

    private String hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to process Offerwall HMAC signature", exception);
        }
    }

    private BigDecimal parseAmount(String value) {
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.stripTrailingZeros().scale() > 4) {
                throw badRequest("Offerwall amounts may contain at most four decimal places");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid Offerwall currency amount");
        }
    }

    private int wholePoints(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.DOWN).intValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Offerwall reward exceeds the coin limit");
        }
    }

    private int storedPoints(double amount) {
        try {
            return BigDecimal.valueOf(amount).intValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Stored Offerwall reward is not a whole number of points");
        }
    }

    private long parseUserId(String value) {
        try {
            long userId = Long.parseLong(requiredValue(value, "userId"));
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid Offerwall user ID");
        }
        throw badRequest("Invalid Offerwall user ID");
    }

    private void requireSameUser(Claim claim, long userId) {
        if (!claim.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Offerwall transaction ID belongs to another user");
        }
    }

    private String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw badRequest("Offerwall callback parameter is missing: " + name);
        }
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
