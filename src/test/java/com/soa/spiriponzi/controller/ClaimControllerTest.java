package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ClaimDto;
import com.soa.spiriponzi.dto.UserStatsDto;
import com.soa.spiriponzi.exception.ClaimException;
import com.soa.spiriponzi.exception.ReCaptchaInvalidException;
import com.soa.spiriponzi.service.CaptchaService;
import com.soa.spiriponzi.service.ClaimService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class ClaimControllerTest {

    private CaptchaService captchaService;
    private ClaimService claimService;
    private ClaimController controller;

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        captchaService = mock(CaptchaService.class);
        claimService = mock(ClaimService.class);
        controller = new ClaimController(captchaService, claimService);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void startClaimChecksCaptchaAndStartsClaimForAuthenticatedUser() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("g-recaptcha-response")).thenReturn("captcha-response");

        var response = controller.startClaim(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(captchaService).processResponse("captcha-response");
        verify(claimService).startClaim(ControllerTestSupport.EMAIL);
    }

    @Test
    void startClaimDoesNotHideUnexpectedClaimServiceFailures() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("g-recaptcha-response")).thenReturn("captcha-response");
        doThrow(new IllegalStateException("claim failed"))
                .when(claimService).startClaim(ControllerTestSupport.EMAIL);

        assertThrows(IllegalStateException.class, () -> controller.startClaim(request));

        verify(captchaService).processResponse("captcha-response");
        verify(claimService).startClaim(ControllerTestSupport.EMAIL);
    }

    @Test
    void startClaimReturnsCooldownStatusWhenUserClaimsTooSoon() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("g-recaptcha-response")).thenReturn("captcha-response");
        doThrow(new ClaimException("You can claim once every hour"))
                .when(claimService).startClaim(ControllerTestSupport.EMAIL);

        var response = controller.startClaim(request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        verify(claimService).startClaim(ControllerTestSupport.EMAIL);
    }

    @Test
    void startClaimReturnsBadRequestWhenCaptchaIsInvalid() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("g-recaptcha-response")).thenReturn("captcha-response");
        doThrow(new ReCaptchaInvalidException("Captcha validation failed"))
                .when(captchaService).processResponse("captcha-response");

        var response = controller.startClaim(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(claimService, org.mockito.Mockito.never()).startClaim(ControllerTestSupport.EMAIL);
    }

    @Test
    void getClaimsReturnsAuthenticatedUsersClaims() {
        List<ClaimDto> claims = List.of(ClaimDto.builder().id(3L).amount(5).build());
        when(claimService.findByUser(ControllerTestSupport.EMAIL)).thenReturn(claims);

        var response = controller.getClaims();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(claims, response.getBody());
        verify(claimService).findByUser(ControllerTestSupport.EMAIL);
    }

    @Test
    void getGameClaimCooldownReturnsServiceValue() {
        when(claimService.getGameClaimCooldownSeconds(ControllerTestSupport.EMAIL)).thenReturn(32L);

        assertEquals(32, controller.getGameClaimCooldownSeconds());

        verify(claimService).getGameClaimCooldownSeconds(ControllerTestSupport.EMAIL);
    }

    @Test
    void startGameClaimReturnsUpdatedUserStats() {
        UserStatsDto stats = UserStatsDto.builder()
                .annualPoints(400)
                .monthlyPoints(180)
                .numberOfClaims(6)
                .build();
        when(claimService.startGameClaim(ControllerTestSupport.EMAIL)).thenReturn(stats);

        assertEquals(stats, controller.startGameClaim());

        verify(claimService).startGameClaim(ControllerTestSupport.EMAIL);
    }
}
