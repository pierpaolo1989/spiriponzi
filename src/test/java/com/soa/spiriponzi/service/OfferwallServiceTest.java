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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferwallServiceTest {

    private static final String PUBLIC_KEY = "offerwall-public-key";
    private static final String SECRET_KEY = "offerwall-secret-key";

    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private OfferwallService service;
    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        service = new OfferwallService(claimRepository, userRepository, userStatsRepository);
        ReflectionTestUtils.setField(service, "publicKey", PUBLIC_KEY);
        ReflectionTestUtils.setField(service, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(service, "requireSignature", true);
        ReflectionTestUtils.setField(service, "earnwallPlacementKey", "earnwall-placement-key");
        ReflectionTestUtils.setField(service, "earnwallSecretKey", "earnwall-secret-key");

        user = User.builder().id(10L).email("user@example.test").build();
        stats = UserStats.builder()
                .annualPoints(50)
                .monthlyPoints(20)
                .numberOfClaims(3)
                .build();
        when(userRepository.findWithLockById(10L)).thenReturn(Optional.of(user));
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        when(claimRepository.findByExternalTransactionId("offerwall:tx-1")).thenReturn(Optional.empty());
        when(claimRepository.findByExternalTransactionId("offerwall:reversal:tx-1")).thenReturn(Optional.empty());
    }

    @Test
    void signedWallUrlIncludesSignatureOverCanonicalEntryParameters() throws Exception {
        String url = service.createSignedWallUrl("10");

        assertTrue(url.contains("/wall/" + PUBLIC_KEY));
        assertTrue(url.contains("userId=10"));
        assertTrue(url.contains("signature=" + hmac("appId=" + PUBLIC_KEY + "&userId=10")));
    }

    @Test
    void earnwallWallUrlUsesThePlacementKeyAndSignedInUserId() {
        assertEquals(
                "https://earnwall.net/offerwall/earnwall-placement-key/10",
                service.createEarnwallWallUrl("10"));
    }

    @Test
    void signedEarnwallPostbackCreditsRewardAndRecordsProviderTransaction() throws Exception {
        earnwallCallback("15.9", "1", "0");

        assertEquals(65, stats.getAnnualPoints());
        assertEquals(35, stats.getMonthlyPoints());
        assertEquals(4, stats.getNumberOfClaims());
        verify(claimRepository).findByExternalTransactionId("earnwall:tx-1");
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void earnwallReversalDeductsTheOriginalReward() throws Exception {
        when(claimRepository.findByExternalTransactionId("earnwall:tx-1"))
                .thenReturn(Optional.of(Claim.builder()
                        .user(user)
                        .amount(15)
                        .externalTransactionId("earnwall:tx-1")
                        .build()));

        earnwallCallback("15.9", "2", "0");

        assertEquals(35, stats.getAnnualPoints());
        assertEquals(5, stats.getMonthlyPoints());
        verify(claimRepository).findByExternalTransactionId("earnwall:reversal:tx-1");
        verify(claimRepository).save(any(Claim.class));
    }

    @Test
    void earnwallDebugPostbackIsAcknowledgedWithoutCrediting() throws Exception {
        earnwallCallback("15.9", "1", "1");

        verify(userRepository, never()).findWithLockById(10L);
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void invalidEarnwallSignatureIsRejectedBeforeLookingUpUser() throws Exception {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.processEarnwallCallback("10", "tx-1", "15.9", "1", "0", "0".repeat(32)));

        assertEquals(401, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    @Test
    void configuredOfferwallCreditsWholePointsAndRecordsTransaction() throws Exception {
        callback("15.9", "credited", "0");

        assertEquals(65, stats.getAnnualPoints());
        assertEquals(35, stats.getMonthlyPoints());
        assertEquals(4, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void duplicateCreditIsNotAppliedTwice() throws Exception {
        when(claimRepository.findByExternalTransactionId("offerwall:tx-1"))
                .thenReturn(Optional.of(Claim.builder().user(user).amount(15).build()));

        callback("15.9", "credited", "0");

        assertEquals(50, stats.getAnnualPoints());
        assertEquals(20, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void reversalDeductsExactlyThePointsOriginallyCredited() throws Exception {
        when(claimRepository.findByExternalTransactionId("offerwall:tx-1"))
                .thenReturn(Optional.of(Claim.builder()
                        .user(user)
                        .amount(15)
                        .externalTransactionId("offerwall:tx-1")
                        .build()));

        callback("-15.9", "reversed", "0");

        assertEquals(35, stats.getAnnualPoints());
        assertEquals(5, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void duplicateReversalIsNotAppliedTwice() throws Exception {
        when(claimRepository.findByExternalTransactionId("offerwall:reversal:tx-1"))
                .thenReturn(Optional.of(Claim.builder().user(user).amount(-15).build()));

        callback("-15.9", "reversed", "0");

        assertEquals(50, stats.getAnnualPoints());
        assertEquals(20, stats.getMonthlyPoints());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void testCallbackIsAcknowledgedWithoutCrediting() throws Exception {
        callback("15.9", "credited", "1");

        verify(userRepository, never()).findWithLockById(10L);
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void invalidSignatureIsRejectedBeforeLookingUpUser() throws Exception {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.processCallback("10", "tx-1", "15.9", "credited", "0", "0".repeat(64)));

        assertEquals(401, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    @Test
    void fractionalAmountsBeyondProviderPrecisionAreRejected() throws Exception {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> callback("1.00001", "credited", "0"));

        assertEquals(400, exception.getStatusCode().value());
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void reversalMustMatchTheOriginalCreditedReward() throws Exception {
        when(claimRepository.findByExternalTransactionId("offerwall:tx-1"))
                .thenReturn(Optional.of(Claim.builder().user(user).amount(15).build()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> callback("-16.0", "reversed", "0"));

        assertEquals(409, exception.getStatusCode().value());
        verify(claimRepository, never()).save(any(Claim.class));
    }

    private void callback(String amount, String status, String test) throws Exception {
        service.processCallback("10", "tx-1", amount, status, test, hmac("10:tx-1:" + amount));
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void earnwallCallback(String reward, String status, String debug) throws Exception {
        String signature = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(
                ("10tx-1" + reward + "earnwall-secret-key").getBytes(StandardCharsets.UTF_8)));
        service.processEarnwallCallback("10", "tx-1", reward, status, debug, signature);
    }
}
