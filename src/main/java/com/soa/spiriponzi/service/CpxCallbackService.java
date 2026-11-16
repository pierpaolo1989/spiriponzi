package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CpxCallbackService {

    private static final Set<String> ALLOWED_SOURCE_IPS = Set.of(
            "157.90.97.92",
            "188.40.3.73",
            "2a01:4f8:d0a:30ff::2");
    private static final String TRANSACTION_PREFIX = "cpx:";

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Value("${cpx.app-security-hash:}")
    private String appSecurityHash;

    public CpxCallbackService(
            ClaimRepository claimRepository,
            UserRepository userRepository,
            UserStatsRepository userStatsRepository) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.userStatsRepository = userStatsRepository;
    }

    @Transactional
    public void process(
            String remoteAddress,
            String forwardedFor,
            String status,
            String transactionId,
            String userIdValue,
            String amountLocalValue,
            String suppliedHash) {
        requireConfiguration();
        verifySourceIp(getClientAddress(remoteAddress, forwardedFor));

        String transactionIdValue = requiredValue(transactionId, "trans_id");
        if (transactionIdValue.length() > 250) {
            throw badRequest("CPX transaction ID is too long");
        }
        verifyHash(transactionIdValue, suppliedHash);

        if (!"1".equals(status)) {
            return;
        }

        long userId = parseUserId(userIdValue);
        BigDecimal amountLocal = parseAmount(amountLocalValue);
        int points;
        try {
            points = amountLocal.intValueExact();
        } catch (ArithmeticException exception) {
            throw badRequest("CPX amount_local must be a whole number of points");
        }
        if (points <= 0) {
            throw badRequest("CPX amount_local must be greater than zero");
        }

        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CPX user not found"));
        String externalTransactionId = TRANSACTION_PREFIX + transactionIdValue;
        var existingTransaction = claimRepository.findByExternalTransactionId(externalTransactionId);
        if (existingTransaction.isPresent()) {
            if (!existingTransaction.get().getUser().getId().equals(userId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "CPX transaction ID belongs to another user");
            }
            return;
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseGet(() -> createUserStats(user));
        try {
            stats.setAnnualPoints(Math.addExact(stats.getAnnualPoints(), points));
            stats.setMonthlyPoints(Math.addExact(stats.getMonthlyPoints(), points));
            stats.setNumberOfClaims(Math.addExact(stats.getNumberOfClaims(), 1));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CPX reward exceeds the coin limit");
        }

        claimRepository.save(Claim.builder()
                .user(user)
                .dateRequest(LocalDateTime.now())
                .amount(amountLocal.doubleValue())
                .externalTransactionId(externalTransactionId)
                .build());
        userStatsRepository.save(stats);
    }

    private String getClientAddress(String remoteAddress, String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String[] forwardedAddresses = forwardedFor.split(",", -1);
        String clientAddress = forwardedAddresses[forwardedAddresses.length - 1].trim();
        if (clientAddress.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CPX callback source IP is not allowed");
        }
        return clientAddress;
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
        if (appSecurityHash == null || appSecurityHash.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "CPX App Security Hash is not configured");
        }
    }

    private void verifySourceIp(String remoteAddress) {
        try {
            byte[] sourceAddress = InetAddress.getByName(remoteAddress).getAddress();
            boolean allowed = ALLOWED_SOURCE_IPS.stream().anyMatch(address -> {
                try {
                    return Arrays.equals(sourceAddress, InetAddress.getByName(address).getAddress());
                } catch (UnknownHostException exception) {
                    throw new IllegalStateException("Invalid CPX source IP configuration", exception);
                }
            });
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CPX callback source IP is not allowed");
            }
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CPX callback source IP is not allowed");
        }
    }

    private void verifyHash(String transactionId, String suppliedHash) {
        String hash = requiredValue(suppliedHash, "hash");
        if (!hash.matches("(?i)[0-9a-f]{32}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "CPX callback hash is invalid");
        }

        String expectedHash;
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(
                    (transactionId + "-" + appSecurityHash).getBytes(StandardCharsets.UTF_8));
            expectedHash = HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to verify CPX callback hash", exception);
        }

        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                hash.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "CPX callback hash is invalid");
        }
    }

    private long parseUserId(String value) {
        try {
            long userId = Long.parseLong(requiredValue(value, "user_id"));
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid CPX user ID");
        }
        throw badRequest("Invalid CPX user ID");
    }

    private BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(requiredValue(value, "amount_local"));
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid CPX amount_local");
        }
    }

    private String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw badRequest("CPX callback parameter is missing: " + name);
        }
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
