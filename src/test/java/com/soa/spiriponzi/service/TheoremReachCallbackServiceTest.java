package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TheoremReachCallbackServiceTest {

    private static final String CALLBACK_URL = "https://example.test/callbacks/theoremreach";
    private static final String API_KEY = "api-key";
    private static final String SECRET_KEY = "test-secret-key";

    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private TheoremReachCallbackService service;
    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        service = new TheoremReachCallbackService(claimRepository, userRepository, userStatsRepository);
        ReflectionTestUtils.setField(service, "apiKey", API_KEY);
        ReflectionTestUtils.setField(service, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(service, "callbackUrl", CALLBACK_URL);

        user = User.builder().id(10L).email("user@example.test").build();
        stats = UserStats.builder()
                .annualPoints(50)
                .monthlyPoints(20)
                .numberOfClaims(3)
                .build();
        when(userRepository.findWithLockById(10L)).thenReturn(Optional.of(user));
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        when(claimRepository.findByExternalTransactionId("theoremreach:tx-1")).thenReturn(Optional.empty());
    }

    @Test
    void iframeUrlUsesTheApiKeyUserIdAndUniqueEntryTransactionId() {
        String url = service.createIframeUrl("10");
        var query = org.springframework.web.util.UriComponentsBuilder.fromUri(URI.create(url))
                .build()
                .getQueryParams();

        assertEquals("https://theoremreach.com/respondent_entry/direct", url.substring(0, url.indexOf('?')));
        assertEquals(API_KEY, query.getFirst("api_key"));
        assertEquals("10", query.getFirst("user_id"));
        assertTrue(query.getFirst("transaction_id").matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void validRewardCreditsPointsAndRecordsTheTransaction() throws Exception {
        service.process(signedPayload(
                "user_id=10&reward=15&currency=0.25&tx_id=tx-1"));

        assertEquals(65, stats.getAnnualPoints());
        assertEquals(35, stats.getMonthlyPoints());
        assertEquals(4, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void duplicateRewardDoesNotCreditTwice() throws Exception {
        when(claimRepository.findByExternalTransactionId("theoremreach:tx-1"))
                .thenReturn(Optional.of(Claim.builder().user(user).amount(15).build()));

        service.process(signedPayload("user_id=10&reward=15&currency=0.25&tx_id=tx-1"));

        assertEquals(50, stats.getAnnualPoints());
        assertEquals(20, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void reversalDeductsTheOriginalRewardOnlyOnce() throws Exception {
        when(claimRepository.findByExternalTransactionId("theoremreach:tx-1"))
                .thenReturn(Optional.of(Claim.builder()
                        .user(user)
                        .amount(15)
                        .externalTransactionId("theoremreach:tx-1")
                        .build()));
        when(claimRepository.findByExternalTransactionId("theoremreach:reversal:tx-1"))
                .thenReturn(Optional.empty());

        service.process(signedPayload(
                "user_id=10&reward=-15&currency=-0.25&tx_id=tx-1&reversal=true"));

        assertEquals(35, stats.getAnnualPoints());
        assertEquals(5, stats.getMonthlyPoints());
        assertEquals(3, stats.getNumberOfClaims());
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void debugCallbackIsAcknowledgedWithoutLookingUpTheUser() throws Exception {
        service.process(signedPayload("debug=true"));

        verify(userRepository, never()).findWithLockById(10L);
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void theoremReachServerTestCallbackIsAcknowledgedWithoutLookingUpTheTestUser() throws Exception {
        service.process(signedPayload(
                "user_id=12345&reward=25&currency={currency}&tx_id=test-tx"
                        + "&debug={debug}&reversal={reversal}&tr_user_id=12345&app_id=26065"
                        + "&tr_reward=25&status=1&tr_currency=0.01&screenout=2&profiler=2"
                        + "&tr_tx_id=test-tx&ip=127.0.0.1&offer_id=campaign_test&tr_debug=true"));

        verify(userRepository, never()).findWithLockById(12345L);
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void invalidHashIsRejectedBeforeLookingUpTheUser() throws Exception {
        String payload = signedPayload("user_id=10&reward=15&currency=0.25&tx_id=tx-1");
        String invalidPayload = payload.substring(0, payload.length() - 1)
                + (payload.endsWith("A") ? "B" : "A");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.process(invalidPayload));

        assertEquals(401, exception.getStatusCode().value());
        verify(userRepository, never()).findWithLockById(10L);
    }

    @Test
    void iframeIsDisabledWhenEitherCredentialIsMissing() {
        ReflectionTestUtils.setField(service, "secretKey", "");

        assertFalse(service.isConfigured());
    }

    private String signedPayload(String unsignedQuery) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String unsignedUrl = CALLBACK_URL + "?" + unsignedQuery;
        String hash = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(unsignedUrl.getBytes(StandardCharsets.UTF_8)));
        return unsignedQuery + "&hash=" + hash;
    }
}
