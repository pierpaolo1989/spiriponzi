package com.soa.spiriponzi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

import org.bitcoinj.core.Address;
import org.bitcoinj.params.MainNetParams;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.model.WithdrawRequest;
import com.soa.spiriponzi.model.enums.WithdrawStatus;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.utils.Constants;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WithdrawServiceImpl implements WithdrawService {
    private static final BigDecimal FEE_EUR = new BigDecimal("0.10");
    private static final Pattern ETH_ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final WithdrawRepository withdrawRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WithdrawDto> findByUser(String email, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("Invalid pagination");
        }
        return withdrawRepository.findByUserEmail(email, PageRequest.of(page, size)).getContent().stream()
                .map(w -> WithdrawDto.builder().id(w.getId()).amount(w.getAmount())
                        .dateRequest(w.getDateRequest()).status(w.getStatus()).currency(w.getCurrency())
                        .transactionId(w.getTransactionId()).destination(w.getDestination())
                        .grossEur(w.getGrossEur()).feeEur(w.getFeeEur()).paidAmount(w.getPaidAmount()).build())
                .toList();
    }

    @Override
    @Transactional
    public void startRequest(String email, WithdrawDto request) {
        if (!Double.isFinite(request.getAmount()) || request.getAmount() != Math.rint(request.getAmount())
                || request.getAmount() < Constants.LIMIT_WITHDRAWAL || request.getAmount() > Integer.MAX_VALUE) {
            throw badRequest("Enter a whole number of points, at least 3000");
        }
        int points = (int) request.getAmount();
        String currency = request.getCurrency();
        String destination = request.getDestination() == null ? "" : request.getDestination().trim();
        validateDestination(currency, destination);
        BigDecimal gross = BigDecimal.valueOf(points).divide(BigDecimal.valueOf(6000), 2, RoundingMode.DOWN);
        if (gross.compareTo(FEE_EUR) <= 0) {
            throw badRequest("The withdrawal must exceed the fee");
        }
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        UserStats stats = userStatsRepository.findWithLockByUserEmail(email)
                .orElseThrow(() -> badRequest("User has no point balance"));
        if (stats.getAnnualPoints() < points) {
            throw badRequest("Insufficient points: requested " + points
                    + ", available " + stats.getAnnualPoints());
        }
        stats.setAnnualPoints(stats.getAnnualPoints() - points);
        userStatsRepository.save(stats);
        withdrawRepository.save(WithdrawRequest.builder().user(user).amount(points).currency(currency)
                .destination(destination).grossEur(gross).feeEur(FEE_EUR)
                .dateRequest(LocalDateTime.now()).status(WithdrawStatus.PENDING.name()).build());
    }

    @Transactional(readOnly = true)
    public Page<WithdrawRequest> pendingRequests(int page) {
        return withdrawRepository.findByStatusInOrderByDateRequestAsc(
                List.of(WithdrawStatus.PENDING.name(), WithdrawStatus.APPROVED.name()),
                PageRequest.of(page, 25));
    }

    @Transactional
    public void approve(long id) {
        WithdrawRequest request = lockedRequest(id, WithdrawStatus.PENDING);
        requireSolvent(request);
        request.setStatus(WithdrawStatus.APPROVED.name());
    }

    @Transactional
    public void reject(long id) {
        WithdrawRequest request = lockedRequest(id, WithdrawStatus.PENDING, WithdrawStatus.APPROVED);
        UserStats stats = lockedStats(request);
        try {
            stats.setAnnualPoints(Math.addExact(stats.getAnnualPoints(), (int) request.getAmount()));
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Point balance exceeds its limit", ex);
        }
        request.setStatus(WithdrawStatus.REJECTED.name());
    }

    @Transactional
    public void archiveLegacy(long id) {
        WithdrawRequest request = withdrawRepository.findWithLockById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Withdrawal not found"));
        if (!WithdrawStatus.PENDING.name().equals(request.getStatus())
                || (request.getDestination() != null && request.getGrossEur() != null && request.getFeeEur() != null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This is not a pending legacy request");
        }
        request.setStatus(WithdrawStatus.REJECTED.name());
    }

    @Transactional
    public void complete(long id, String reference, BigDecimal paidAmount) {
        WithdrawRequest request = lockedRequest(id, WithdrawStatus.APPROVED);
        String cleanReference = reference == null ? "" : reference.trim();
        if (cleanReference.isEmpty() || cleanReference.length() > 128 || cleanReference.chars().anyMatch(Character::isISOControl)) {
            throw badRequest("A valid transaction reference is required");
        }
        int scale = "BTC".equals(request.getCurrency()) ? 8 : "ETH".equals(request.getCurrency()) ? 18 : 2;
        if (paidAmount == null || paidAmount.signum() <= 0 || paidAmount.precision() > 30
                || paidAmount.stripTrailingZeros().scale() > scale
                || paidAmount.compareTo(BigDecimal.valueOf(1_000_000_000_000L)) >= 0) {
            throw badRequest("Invalid amount paid");
        }
        if ("PAYPAL".equals(request.getCurrency())
                && paidAmount.compareTo(request.getGrossEur().subtract(request.getFeeEur())) != 0) {
            throw badRequest("PayPal amount must match the net amount in euros");
        }
        request.setTransactionId(cleanReference);
        request.setPaidAmount(paidAmount);
        request.setStatus(WithdrawStatus.COMPLETED.name());
    }

    private WithdrawRequest lockedRequest(long id, WithdrawStatus... expected) {
        WithdrawRequest request = withdrawRepository.findWithLockById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Withdrawal not found"));
        if (java.util.Arrays.stream(expected).noneMatch(status -> status.name().equals(request.getStatus()))
                || request.getDestination() == null
                || request.getGrossEur() == null || request.getFeeEur() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Withdrawal cannot be processed; review legacy requests manually");
        }
        return request;
    }

    private UserStats lockedStats(WithdrawRequest request) {
        return userStatsRepository.findWithLockByUserEmail(request.getUser().getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Point balance not found"));
    }

    private void requireSolvent(WithdrawRequest request) {
        if (lockedStats(request).getAnnualPoints() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Balance is negative; review reversed rewards before paying");
        }
    }

    private static void validateDestination(String currency, String destination) {
        if (destination.length() > 254 || currency == null) {
            throw badRequest("Invalid payment destination");
        }
        switch (currency) {
            case "BTC":
                try {
                    Address.fromString(MainNetParams.get(), destination);
                } catch (IllegalArgumentException ex) {
                    throw badRequest("Invalid Bitcoin mainnet address");
                }
                break;
            case "ETH":
                if (!validEthereumAddress(destination)) {
                    throw badRequest("Invalid Ethereum mainnet address or checksum");
                }
                break;
            case "PAYPAL":
                if (destination.isEmpty() || !VALIDATOR.validateValue(PaypalEmail.class, "email", destination).isEmpty()) {
                    throw badRequest("Invalid PayPal email");
                }
                break;
            default:
                throw badRequest("Unsupported payment method");
        }
    }

    private static boolean validEthereumAddress(String address) {
        if (!ETH_ADDRESS.matcher(address).matches()) {
            return false;
        }
        String original = address.substring(2);
        String lower = original.toLowerCase(Locale.ROOT);
        if (lower.equals("0000000000000000000000000000000000000000")) {
            return false;
        }
        if (original.equals(lower) || original.equals(original.toUpperCase(Locale.ROOT))) {
            return true;
        }
        byte[] bytes = lower.getBytes(StandardCharsets.US_ASCII);
        KeccakDigest digest = new KeccakDigest(256);
        digest.update(bytes, 0, bytes.length);
        byte[] hash = new byte[32];
        digest.doFinal(hash, 0);
        for (int i = 0; i < original.length(); i++) {
            char character = original.charAt(i);
            if (character >= '0' && character <= '9') {
                continue;
            }
            int nibble = (hash[i / 2] >> (i % 2 == 0 ? 4 : 0)) & 0xF;
            if (Character.isUpperCase(character) != (nibble >= 8)) {
                return false;
            }
        }
        return true;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static class PaypalEmail {
        @jakarta.validation.constraints.Email
        private String email;
    }
}
