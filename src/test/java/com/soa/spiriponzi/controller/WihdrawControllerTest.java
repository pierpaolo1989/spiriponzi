package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.service.WithdrawService;
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

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        withdrawService = mock(WithdrawService.class);
        controller = new WihdrawController(withdrawService);
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
        WithdrawDto request = WithdrawDto.builder().amount(15).currency("BTC").build();

        ResponseEntity<Void> response = controller.startWithdrawRequest(request);

        assertEquals(200, response.getStatusCode().value());
        verify(withdrawService).startRequest(ControllerTestSupport.EMAIL, request);
    }
}
