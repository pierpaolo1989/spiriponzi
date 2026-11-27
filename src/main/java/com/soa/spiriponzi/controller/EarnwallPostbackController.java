package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.service.OfferwallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/earnwall/postback")
public class EarnwallPostbackController {

    private final OfferwallService offerwallService;

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> postback(
            @RequestParam(name = "subId", required = false) String userId,
            @RequestParam(name = "transId", required = false) String transactionId,
            @RequestParam(name = "reward", required = false) String reward,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "debug", required = false) String debug,
            @RequestParam(name = "signature", required = false) String signature) {
        offerwallService.processEarnwallCallback(userId, transactionId, reward, status, debug, signature);
        return ResponseEntity.ok("ok");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handlePostbackError(ResponseStatusException exception) {
        log.warn("EarnWall postback rejected: HTTP {} - {}", exception.getStatusCode().value(), exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).body("Postback rejected");
    }
}
