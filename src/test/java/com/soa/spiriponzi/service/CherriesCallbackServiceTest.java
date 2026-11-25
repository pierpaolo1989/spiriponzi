package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CherriesCallbackServiceTest {

    private static final String CALLBACK_URL = "https://example.test/callbacks/cherries";
    private static final String HASH_KEY = "test-hash-key";

    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private CherriesCallbackService service;
    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        service = new CherriesCallbackService(claimRepository, userRepository, userStatsRepository);
        ReflectionTestUtils.setField(service, "supplierId", "supplier-123");
        ReflectionTestUtils.setField(service, "hashKey", HASH_KEY);
        ReflectionTestUtils.setField(service, "callbackUrl", CALLBACK_URL);

        user = User.builder().id(10L).email("user@example.test").build();
        stats = UserStats.builder()
                .annualPoints(50)
                .monthlyPoints(20)
                .numberOfClaims(3)
                .build();
        when(userRepository.findWithLockById(10L)).thenReturn(Optional.of(user));
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        when(claimRepository.findByExternalTransactionId("cherries:tx-1")).thenReturn(Optional.empty());
    }

    @Test
    void iframeUrlContainsPanelistAndSupplierIds() {
        String url = service.createIframeUrl("10");

        assertEquals("https://grabcherries.com/wall?pid=10&sid=supplier-123", url);
    }

    @Test
    void validCompletionCreditsWholePointsAndRecordsTransaction() throws Exception {
        service.process(signedPayload(
                "pid=10&status=100&subcode=100&transaction_id=tx-1&user_reward=15"));

        assertEquals(65, stats.getAnnualPoints());
        assertEquals(35, stats.getMonthlyPoints());
        assertEquals(4, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void duplicateCompletionDoesNotCreditTwice() throws Exception {
        when(claimRepository.findByExternalTransactionId("cherries:tx-1"))
                .thenReturn(Optional.of(Claim.builder().user(user).amount(15).build()));

        service.process(signedPayload(
                "pid=10&status=100&subcode=100&transaction_id=tx-1&user_reward=15"));

        assertEquals(50, stats.getAnnualPoints());
        assertEquals(20, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void reversalDeductsTheOriginalRewardExactlyOnce() throws Exception {
        when(claimRepository.findByExternalTransactionId("cherries:tx-1"))
                .thenReturn(Optional.of(Claim.builder()
                        .user(user)
                        .amount(15)
                        .externalTransactionId("cherries:tx-1")
                        .build()));
        when(claimRepository.findByExternalTransactionId("cherries:reversal:tx-1"))
                .thenReturn(Optional.empty());

        service.process(signedPayload(
                "pid=10&status=100&subcode=301&transaction_id=tx-1&user_reward=-15"));

        assertEquals(35, stats.getAnnualPoints());
        assertEquals(5, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void nonCompletionDoesNotCreditUser() throws Exception {
        service.process(signedPayload(
                "pid=10&status=200&subcode=202&transaction_id=screenout&user_reward=0"));

        verify(userRepository, never()).findWithLockById(10L);
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void invalidHashIsRejectedBeforeLookingUpUser() throws Exception {
        String payload = signedPayload(
                "pid=10&status=100&subcode=100&transaction_id=tx-1&user_reward=15");
        String invalidPayload = payload.substring(0, payload.length() - 1)
                + (payload.endsWith("A") ? "B" : "A");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.process(invalidPayload));

        assertEquals(401, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    @Test
    void iframeRemainsDisabledWhenSupplierOrHashKeyIsMissing() {
        ReflectionTestUtils.setField(service, "hashKey", "");

        assertFalse(service.isConfigured());
    }

    private String signedPayload(String unsignedPayload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HASH_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String unsignedUrl = CALLBACK_URL + "?" + unsignedPayload;
        String hash = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(unsignedUrl.getBytes(StandardCharsets.UTF_8)));
        return unsignedPayload + "&hash=" + hash;
    }
}
