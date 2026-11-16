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

class CpxCallbackServiceTest {

    private static final String APP_SECURITY_HASH = "test-app-security-hash";
    private static final String ALLOWED_IP = "157.90.97.92";

    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private CpxCallbackService service;
    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        service = new CpxCallbackService(claimRepository, userRepository, userStatsRepository);
        ReflectionTestUtils.setField(service, "appSecurityHash", APP_SECURITY_HASH);

        user = User.builder().id(10L).email("user@example.test").build();
        stats = UserStats.builder().annualPoints(50).monthlyPoints(20).numberOfClaims(3).build();
        when(userRepository.findWithLockById(10L)).thenReturn(Optional.of(user));
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        when(claimRepository.findByExternalTransactionId("cpx:tx-1")).thenReturn(Optional.empty());
    }

    @Test
    void validCompletionCreditsAmountLocalAndRecordsTransactionId() throws Exception {
        service.process(ALLOWED_IP, null, "1", "tx-1", "10", "15", callbackHash("tx-1"));

        assertEquals(65, stats.getAnnualPoints());
        assertEquals(35, stats.getMonthlyPoints());
        assertEquals(4, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void duplicateTransactionDoesNotCreditTwice() throws Exception {
        when(claimRepository.findByExternalTransactionId("cpx:tx-1"))
                .thenReturn(Optional.of(Claim.builder().user(user).amount(15).build()));

        service.process(ALLOWED_IP, null, "1", "tx-1", "10", "15", callbackHash("tx-1"));

        assertEquals(50, stats.getAnnualPoints());
        assertEquals(20, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void nonCompletionStatusDoesNotCredit() throws Exception {
        service.process(ALLOWED_IP, null, "2", "tx-1", "10", "15", callbackHash("tx-1"));

        verify(userRepository, never()).findWithLockById(10L);
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void invalidHashIsRejectedBeforeLookingUpUser() throws Exception {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.process(
                        ALLOWED_IP, null, "1", "tx-1", "10", "15", "00000000000000000000000000000000"));

        assertEquals(401, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    @Test
    void nonCpxSourceIpIsRejectedBeforeLookingUpUser() throws Exception {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.process(
                        "192.0.2.1", null, "1", "tx-1", "10", "15", callbackHash("tx-1")));

        assertEquals(403, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    @Test
    void fractionalAmountLocalIsRejected() throws Exception {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.process(ALLOWED_IP, null, "1", "tx-1", "10", "1.5", callbackHash("tx-1")));

        assertEquals(400, exception.getStatusCode().value());
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void renderForwardedForUsesTheLastAddressAsTheClient() throws Exception {
        service.process(
                "10.0.0.1",
                "192.0.2.10, " + ALLOWED_IP,
                "1",
                "tx-1",
                "10",
                "15",
                callbackHash("tx-1"));

        assertEquals(65, stats.getAnnualPoints());
        verify(claimRepository).save(any(Claim.class));
    }

    @Test
    void renderForwardedForDoesNotTrustAnEarlierSpoofedAddress() throws Exception {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.process(
                        "10.0.0.1",
                        ALLOWED_IP + ", 192.0.2.10",
                        "1",
                        "tx-1",
                        "10",
                        "15",
                        callbackHash("tx-1")));

        assertEquals(403, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    private String callbackHash(String transactionId) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(
                (transactionId + "-" + APP_SECURITY_HASH).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
