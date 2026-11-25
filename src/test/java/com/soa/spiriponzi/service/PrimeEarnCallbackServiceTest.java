package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
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

class PrimeEarnCallbackServiceTest {

    private static final String CALLBACK_URL = "https://example.test/callbacks/primeearn";
    private static final String SALT = "test-salt";

    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private PrimeEarnCallbackService service;
    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        service = new PrimeEarnCallbackService(claimRepository, userRepository, userStatsRepository);
        ReflectionTestUtils.setField(service, "postbackSalt", SALT);
        ReflectionTestUtils.setField(service, "callbackUrl", CALLBACK_URL);

        user = User.builder().id(10L).email("user@example.test").build();
        stats = UserStats.builder()
                .annualPoints(50)
                .monthlyPoints(20)
                .numberOfClaims(3)
                .build();
        when(userRepository.findWithLockById(10L)).thenReturn(Optional.of(user));
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        when(claimRepository.findByExternalTransactionId("primeearn:tx-1")).thenReturn(Optional.empty());
    }

    @Test
    void validCompletionCreditsRewardAndRecordsTransactionId() throws Exception {
        service.process(signedQuery("user=10&tx_id=tx-1&reward=15&type=1&payout=0.15"));

        assertEquals(65, stats.getAnnualPoints());
        assertEquals(35, stats.getMonthlyPoints());
        assertEquals(4, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void repeatedTransactionDoesNotCreditTwice() throws Exception {
        when(claimRepository.findByExternalTransactionId("primeearn:tx-1"))
                .thenReturn(Optional.of(Claim.builder().user(user).amount(15).build()));

        service.process(signedQuery("user=10&tx_id=tx-1&reward=15&type=1"));

        assertEquals(50, stats.getAnnualPoints());
        assertEquals(20, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void reconciliationReversesOnlyTheOriginalUsersReward() throws Exception {
        Claim original = Claim.builder()
                .user(user)
                .amount(15)
                .externalTransactionId("primeearn:original-tx")
                .build();
        when(claimRepository.findByExternalTransactionId("primeearn:original-tx"))
                .thenReturn(Optional.of(original));
        when(claimRepository.findByExternalTransactionId("primeearn:reverse-tx"))
                .thenReturn(Optional.empty());

        service.process(signedQuery(
                "user=10&tx_id=reverse-tx&reward=-15&type=4&reconciled_tx_id=original-tx"));

        assertEquals(35, stats.getAnnualPoints());
        assertEquals(5, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void invalidHashIsRejectedBeforeLookingUpUser() throws Exception {
        String query = signedQuery("user=10&tx_id=tx-1&reward=15&type=1");
        String invalidQuery = query.substring(0, query.length() - 1)
                + (query.endsWith("0") ? "1" : "0");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.process(invalidQuery));

        assertEquals(401, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    @Test
    void screenoutWithoutRewardDoesNotCreateClaim() throws Exception {
        service.process(signedQuery("user=10&tx_id=screenout-tx&reward=0&type=2"));

        verify(userRepository, never()).findWithLockById(10L);
        verify(claimRepository, never()).save(any(Claim.class));
    }

    private String signedQuery(String unsignedQuery) throws Exception {
        String value = CALLBACK_URL + "?" + unsignedQuery + SALT;
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        return unsignedQuery + "&hash=" + hash;
    }
}
