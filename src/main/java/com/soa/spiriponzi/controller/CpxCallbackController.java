package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.service.CpxCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/callbacks/cpx")
public class CpxCallbackController {

    private final CpxCallbackService callbackService;

    @GetMapping
    public ResponseEntity<String> callback(
            HttpServletRequest request,
            @RequestParam("status") String status,
            @RequestParam("trans_id") String transactionId,
            @RequestParam("user_id") String userId,
            @RequestParam("amount_local") String amountLocal,
            @RequestParam("hash") String hash) {
        callbackService.process(
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                status,
                transactionId,
                userId,
                amountLocal,
                hash);
        return ResponseEntity.ok("OK");
    }
}
