package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.service.WithdrawService;
import com.soa.spiriponzi.service.CaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WihdrawControllerTest {

    private WithdrawService withdrawService;
    private WihdrawController controller;
    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        withdrawService = mock(WithdrawService.class);
        captchaService = mock(CaptchaService.class);
        controller = new WihdrawController(withdrawService, captchaService);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void findMenuByUserPassesPaginationAndAuthenticatedEmail() {
        List<WithdrawDto> requests = List.of(WithdrawDto.builder().id(7L).amount(20).currency("BTC").build());
        when(withdrawService.findByUser(ControllerTestSupport.EMAIL, 2, 10)).thenReturn(requests);

        assertEquals(requests, controller.findMenuByUser(2, 10));

        verify(withdrawService).findByUser(ControllerTestSupport.EMAIL, 2, 10);
    }

    @Test
    void startWithdrawRequestDelegatesAndReturnsSuccess() {
        when(captchaService.getReCaptchaSite()).thenReturn("site-key");
        when(captchaService.getReCaptchaSecret()).thenReturn("secret-key");
        WithdrawDto request = WithdrawDto.builder().amount(15).currency("BTC").captchaResponse("token").build();

        ResponseEntity<Void> response = controller.startWithdrawRequest(request);

        assertEquals(200, response.getStatusCode().value());
        verify(withdrawService).startRequest(ControllerTestSupport.EMAIL, request);
        verify(captchaService).processResponse("token");
    }

    @Test
    void rejectsWithdrawalsWhenOnlyTestCaptchaIsConfigured() {
        when(captchaService.getReCaptchaSite()).thenReturn("6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI");
        when(captchaService.getReCaptchaSecret()).thenReturn("secret-key");
        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> controller.startWithdrawRequest(WithdrawDto.builder().build()));
        assertEquals(503, exception.getStatusCode().value());
    }
}
