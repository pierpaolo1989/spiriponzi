package com.soa.spiriponzi.service;

import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminMfaService {

    private static final String USERNAME_ATTRIBUTE = "adminMfaUsername";
    private static final String CODE_HASH_ATTRIBUTE = "adminMfaCodeHash";
    private static final String EXPIRES_AT_ATTRIBUTE = "adminMfaExpiresAt";
    private static final String ATTEMPTS_ATTRIBUTE = "adminMfaAttempts";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(5);

    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminMfaService(PasswordEncoder passwordEncoder, Clock clock) {
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public String createChallenge(HttpSession session, String username) {
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        session.setAttribute(USERNAME_ATTRIBUTE, username);
        session.setAttribute(CODE_HASH_ATTRIBUTE, passwordEncoder.encode(code));
        session.setAttribute(EXPIRES_AT_ATTRIBUTE, clock.instant().plus(CODE_LIFETIME).toEpochMilli());
        session.setAttribute(ATTEMPTS_ATTRIBUTE, 0);
        return code;
    }

    public boolean hasPendingChallenge(HttpSession session) {
        if (session == null
                || !(session.getAttribute(USERNAME_ATTRIBUTE) instanceof String)
                || !(session.getAttribute(CODE_HASH_ATTRIBUTE) instanceof String)
                || !(session.getAttribute(EXPIRES_AT_ATTRIBUTE) instanceof Long)) {
            return false;
        }
        long expiresAt = (Long) session.getAttribute(EXPIRES_AT_ATTRIBUTE);
        if (!clock.instant().isBefore(java.time.Instant.ofEpochMilli(expiresAt))) {
            clearChallenge(session);
            return false;
        }
        return true;
    }

    public VerificationResult verify(HttpSession session, String submittedCode) {
        if (!hasPendingChallenge(session)) {
            return new VerificationResult(Status.EXPIRED, null);
        }

        String codeHash = (String) session.getAttribute(CODE_HASH_ATTRIBUTE);
        int attempts = (Integer) session.getAttribute(ATTEMPTS_ATTRIBUTE);
        boolean matches = submittedCode != null
                && submittedCode.matches("\\d{6}")
                && passwordEncoder.matches(submittedCode, codeHash);

        if (matches) {
            String username = (String) session.getAttribute(USERNAME_ATTRIBUTE);
            clearChallenge(session);
            return new VerificationResult(Status.VERIFIED, username);
        }

        attempts++;
        if (attempts >= MAX_ATTEMPTS) {
            clearChallenge(session);
            return new VerificationResult(Status.LOCKED, null);
        }
        session.setAttribute(ATTEMPTS_ATTRIBUTE, attempts);
        return new VerificationResult(Status.INVALID, null);
    }

    public void clearChallenge(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(USERNAME_ATTRIBUTE);
        session.removeAttribute(CODE_HASH_ATTRIBUTE);
        session.removeAttribute(EXPIRES_AT_ATTRIBUTE);
        session.removeAttribute(ATTEMPTS_ATTRIBUTE);
    }

    public enum Status {
        VERIFIED,
        INVALID,
        EXPIRED,
        LOCKED
    }

    public record VerificationResult(Status status, String username) {
    }
}
