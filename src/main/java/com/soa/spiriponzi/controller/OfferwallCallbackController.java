package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.service.OfferwallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/callbacks/offerwall")
public class OfferwallCallbackController {

    private final OfferwallService offerwallService;

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> callback(
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "transactionId", required = false) String transactionId,
            @RequestParam(name = "currencyAmount", required = false) String currencyAmount,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "test", required = false) String test,
            @RequestParam(name = "signature", required = false) String signature) {
        offerwallService.processCallback(userId, transactionId, currencyAmount, status, test, signature);
        return ResponseEntity.ok("OK");
    }
}
