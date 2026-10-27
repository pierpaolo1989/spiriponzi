package com.soa.spiriponzi.controller.rest;

import com.soa.spiriponzi.controller.ControllerTestSupport;
import com.soa.spiriponzi.dto.AdBudgetDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.ADService;
import com.soa.spiriponzi.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdControllerTest {

    private ADService adService;
    private UserService userService;
    private UserAdController controller;
    private final User user = User.builder().id(6L).email(ControllerTestSupport.EMAIL).build();

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        adService = mock(ADService.class);
        userService = mock(UserService.class);
        controller = new UserAdController(adService, userService);
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(user);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void createAdDelegatesForAuthenticatedUser() {
        UserADDto ad = UserADDto.builder().title("Ad").build();

        controller.createAd(ad);

        verify(adService).create(user, ad);
    }

    @Test
    void toggleActiveReturnsServiceResult() {
        when(adService.toggleActive(user, 4L)).thenReturn(true);

        assertTrue(controller.toggleActive(4L));

        verify(adService).toggleActive(user, 4L);
    }

    @Test
    void getAvailableBalanceReturnsServiceBalance() {
        when(adService.getAvailableBalance(user)).thenReturn(75.0);

        assertEquals(75, controller.getAvailableBalance());

        verify(adService).getAvailableBalance(user);
    }

    @Test
    void deleteAdDelegatesForAuthenticatedUser() {
        controller.deleteAd(8L);

        verify(adService).delete(user, 8L);
    }

    @Test
    void updateBudgetReturnsUpdatedAvailableBalance() {
        AdBudgetDto budget = new AdBudgetDto();
        budget.setBudget(30);
        when(adService.updateBudget(user, 2L, 30)).thenReturn(45.0);

        assertEquals(45, controller.updateBudget(2L, budget));

        verify(adService).updateBudget(user, 2L, 30);
    }
}
