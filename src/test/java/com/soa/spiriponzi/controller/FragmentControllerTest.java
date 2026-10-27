package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
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
import static org.mockito.Mockito.when;

class FragmentControllerTest {

    private ADService adService;
    private UserService userService;
    private FragmentController controller;
    private final User user = User.builder()
            .id(15L)
            .email(ControllerTestSupport.EMAIL)
            .userStats(UserStats.builder().annualPoints(80).build())
            .build();

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        adService = mock(ADService.class);
        userService = mock(UserService.class);
        controller = new FragmentController(adService, userService);
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(user);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void getAdsFragmentAddsProposedAds() {
        List<ADDto> ads = List.of(ADDto.builder().id(4L).title("Ad").build());
        when(adService.getProposedAds(15L)).thenReturn(ads);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("fragments/ads :: adList", controller.getAdsFragment(model));

        assertEquals(ads, model.get("ads"));
    }

    @Test
    void getSummaryFragmentAddsUser() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("fragments/summary :: summary", controller.getSummaryFragment(model));

        assertEquals(user, model.get("user"));
    }
}
