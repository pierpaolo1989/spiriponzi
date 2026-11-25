package com.soa.spiriponzi.service;

import java.util.Optional;

import com.soa.spiriponzi.model.AffiliateClick;
import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.AffiliateClickRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffiliateServiceTest {

    private AffiliateClickRepository affiliateClickRepository;
    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private AffiliateService service;
    private User affiliate;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        affiliateClickRepository = mock(AffiliateClickRepository.class);
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        service = new AffiliateService(
                affiliateClickRepository, claimRepository, userRepository, userStatsRepository);

        affiliate = User.builder().id(10L).email("affiliate@example.test").build();
        stats = UserStats.builder().annualPoints(50).monthlyPoints(20).numberOfClaims(3).build();
        when(userRepository.findByReferralCode("AFFILIATE10")).thenReturn(Optional.of(affiliate));
        when(userStatsRepository.findWithLockByUserEmail(affiliate.getEmail())).thenReturn(Optional.of(stats));
    }

    @Test
    void qualifyingHundredthClickCreditsCoinsAndRecordsClaim() {
        when(affiliateClickRepository.countByAffiliateUser_Id(10L)).thenReturn(99L);

        AffiliateService.ClickResult result = service.trackClick("AFFILIATE10", null, null);

        assertTrue(result.counted());
        assertTrue(result.setVisitorCookie());
        assertEquals(43, result.visitorToken().length());
        assertEquals(100, result.totalClicks());
        assertEquals(10, result.awardedCoins());
        assertEquals(60, stats.getAnnualPoints());
        assertEquals(30, stats.getMonthlyPoints());
        assertEquals(4, stats.getNumberOfClaims());
        verify(affiliateClickRepository).save(any(AffiliateClick.class));
        verify(claimRepository).save(any(Claim.class));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void repeatedBrowserClickWithinTwentyFourHoursIsNotCounted() {
        String visitorToken = "A".repeat(43);
        when(affiliateClickRepository.existsByAffiliateUser_IdAndVisitorHashAndClickedAtAfter(
                any(), any(), any())).thenReturn(true);
        when(affiliateClickRepository.countByAffiliateUser_Id(10L)).thenReturn(99L);

        AffiliateService.ClickResult result = service.trackClick("AFFILIATE10", visitorToken, null);

        assertFalse(result.counted());
        assertFalse(result.setVisitorCookie());
        assertEquals(99, result.totalClicks());
        assertEquals(0, result.awardedCoins());
        assertEquals(50, stats.getAnnualPoints());
        verify(affiliateClickRepository, never()).save(any(AffiliateClick.class));
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void affiliateOwnerClicksAreExcluded() {
        AffiliateService.ClickResult result = service.trackClick(
                "AFFILIATE10", "A".repeat(43), "AFFILIATE@example.test");

        assertFalse(result.counted());
        verify(userStatsRepository, never()).findWithLockByUserEmail(affiliate.getEmail());
        verify(affiliateClickRepository, never()).save(any(AffiliateClick.class));
    }
}
