package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.service.ADService;
import com.soa.spiriponzi.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class AdControllerTest {

    private UserService userService;
    private ADService adService;
    private AdController controller;
    private final User user = User.builder().id(12L).email(ControllerTestSupport.EMAIL).build();

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        userService = mock(UserService.class);
        adService = mock(ADService.class);
        controller = new AdController(userService, adService);
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(user);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void surfAdsLoadsUserAndProposedAds() {
        ExtendedModelMap model = new ExtendedModelMap();
        List<ADDto> ads = List.of(ADDto.builder().id(4L).build());
        when(adService.getProposedAds(12L)).thenReturn(ads);

        assertEquals("create-ads", controller.surfAds(model));

        assertEquals(user, model.get("user"));
        assertEquals(ads, model.get("ads"));
        verify(userService).findByEmail(ControllerTestSupport.EMAIL);
        verify(adService).getProposedAds(12L);
    }

    @Test
    void createUserAdDelegatesAndReturnsCurrentStats() {
        UserStats stats = UserStats.builder()
                .annualPoints(50)
                .monthlyPoints(20)
                .numberOfClaims(3)
                .withdrawals(1)
                .build();
        when(userService.findStatsByEmail(ControllerTestSupport.EMAIL)).thenReturn(stats);

        var result = controller.createUserAd(8L);

        assertEquals(50, result.getAnnualPoints());
        assertEquals(20, result.getMonthlyPoints());
        assertEquals(3, result.getNumberOfClaims());
        assertEquals(1, result.getWithdrawals());
        verify(userService).createUserAd(user, 8L);
        verify(userService).findStatsByEmail(ControllerTestSupport.EMAIL);
    }

    @Test
    void getAdsReturnsSurfHistoryAndAddsUserToModel() {
        ExtendedModelMap model = new ExtendedModelMap();
        List<UserADDto> ads = List.of(UserADDto.builder().id(5L).title("Visited").build());
        when(adService.findSurfedAdsByUser(12L)).thenReturn(ads);

        assertEquals(ads, controller.getAds(model));

        assertEquals(user, model.get("user"));
        verify(adService).findSurfedAdsByUser(12L);
    }

    @Test
    void getProposedAdsReturnsAdsForAuthenticatedUser() {
        List<ADDto> ads = List.of(ADDto.builder().id(9L).title("Proposed").build());
        when(adService.getProposedAds(12L)).thenReturn(ads);

        assertEquals(ads, controller.getProposedAds());

        verify(adService).getProposedAds(12L);
        verifyNoMoreInteractions(adService);
    }
}
