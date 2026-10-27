package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private UserService userService;
    private UserController controller;

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        userService = mock(UserService.class);
        controller = new UserController(userService);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void getUserReturnsStatsForAuthenticatedUser() {
        UserStats stats = UserStats.builder()
                .annualPoints(100)
                .monthlyPoints(25)
                .numberOfClaims(4)
                .withdrawals(2)
                .build();
        when(userService.findStatsByEmail(ControllerTestSupport.EMAIL)).thenReturn(stats);

        var response = controller.getUser();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(100, response.getBody().getAnnualPoints());
        assertEquals(25, response.getBody().getMonthlyPoints());
        assertEquals(4, response.getBody().getNumberOfClaims());
        assertEquals(2, response.getBody().getWithdrawals());
        verify(userService).findStatsByEmail(ControllerTestSupport.EMAIL);
    }
}
