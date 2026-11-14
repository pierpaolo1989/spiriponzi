package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitLabsCallbackServiceTest {

    private static final String CALLBACK_URL = "https://example.test/callbacks/bitlabs";
    private static final String SECRET = "test-secret";

    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private BitLabsCallbackService service;
    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        service = new BitLabsCallbackService(claimRepository, userRepository, userStatsRepository);
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "callbackUrl", CALLBACK_URL);

        user = User.builder().id(10L).email("user@example.test").build();
        stats = UserStats.builder()
                .annualPoints(50)
                .monthlyPoints(20)
                .numberOfClaims(3)
                .build();
        when(userRepository.findWithLockById(10L)).thenReturn(Optional.of(user));
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        when(claimRepository.findByExternalTransactionId("tx-1")).thenReturn(Optional.empty());
    }

    @Test
    void validCompletionCreditsWholeCoinsAndRecordsTransactionId() throws Exception {
        service.process(callbackQuery("uid=10&tx=tx-1&val=15&raw=survey&activity_type=COMPLETE"));

        assertEquals(65, stats.getAnnualPoints());
        assertEquals(35, stats.getMonthlyPoints());
        assertEquals(4, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void repeatedTransactionDoesNotCreditTwice() throws Exception {
        when(claimRepository.findByExternalTransactionId("tx-1"))
                .thenReturn(Optional.of(Claim.builder().user(user).amount(15).build()));

        service.process(callbackQuery("uid=10&tx=tx-1&val=15&raw=survey&activity_type=COMPLETE"));

        assertEquals(50, stats.getAnnualPoints());
        assertEquals(20, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void reconciliationRecordsNegativeAdjustmentWithoutIncrementingClaimCount() throws Exception {
        Claim original = Claim.builder().user(user).amount(15).build();
        when(claimRepository.findByExternalTransactionId("original-tx")).thenReturn(Optional.of(original));

        service.process(callbackQuery(
                "uid=10&tx=tx-2&val=-5&raw=survey&activity_type=RECONCILIATION&ref=original-tx"));

        assertEquals(45, stats.getAnnualPoints());
        assertEquals(15, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void invalidHashIsRejectedBeforeLookingUpUser() throws Exception {
        String validQuery = callbackQuery("uid=10&tx=tx-1&val=15&raw=survey&activity_type=COMPLETE");
        String query = validQuery.substring(0, validQuery.length() - 1)
                + (validQuery.endsWith("0") ? "1" : "0");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.process(query));

        assertEquals(401, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    @Test
    void fractionalRewardsAreRejectedInsteadOfSilentlyRounded() throws Exception {
        String query = callbackQuery("uid=10&tx=tx-1&val=1.5&raw=survey&activity_type=COMPLETE");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.process(query));

        assertEquals(400, exception.getStatusCode().value());
        verify(claimRepository, never()).save(any(Claim.class));
    }

    private String callbackQuery(String unsignedQuery) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] digest = mac.doFinal((CALLBACK_URL + "?" + unsignedQuery).getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hash.append(String.format("%02x", value & 0xff));
        }
        return unsignedQuery + "&hash=" + hash;
    }
}
