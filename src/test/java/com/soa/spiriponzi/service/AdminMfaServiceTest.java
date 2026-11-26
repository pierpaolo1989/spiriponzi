package com.soa.spiriponzi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminMfaServiceTest {

    private MutableClock clock;
    private AdminMfaService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-26T08:00:00Z"));
        service = new AdminMfaService(new BCryptPasswordEncoder(), clock);
    }

    @Test
    void validCodeIsSingleUse() {
        MockHttpSession session = new MockHttpSession();
        String code = service.createChallenge(session, "admin");

        assertTrue(code.matches("\\d{6}"));
        assertTrue(service.hasPendingChallenge(session));
        var result = service.verify(session, code);

        assertEquals(AdminMfaService.Status.VERIFIED, result.status());
        assertEquals("admin", result.username());
        assertFalse(service.hasPendingChallenge(session));
        assertEquals(AdminMfaService.Status.EXPIRED, service.verify(session, code).status());
    }

    @Test
    void challengeExpiresAfterFiveMinutes() {
        MockHttpSession session = new MockHttpSession();
        String code = service.createChallenge(session, "admin");
        clock.advance(Duration.ofMinutes(5));

        assertEquals(AdminMfaService.Status.EXPIRED, service.verify(session, code).status());
        assertFalse(service.hasPendingChallenge(session));
    }

    @Test
    void fifthIncorrectCodeLocksTheChallenge() {
        MockHttpSession session = new MockHttpSession();
        String correctCode = service.createChallenge(session, "admin");
        String incorrectCode = correctCode.equals("000000") ? "000001" : "000000";

        for (int attempt = 1; attempt < 5; attempt++) {
            assertEquals(AdminMfaService.Status.INVALID,
                    service.verify(session, incorrectCode).status());
        }
        assertEquals(AdminMfaService.Status.LOCKED, service.verify(session, incorrectCode).status());
        assertFalse(service.hasPendingChallenge(session));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
