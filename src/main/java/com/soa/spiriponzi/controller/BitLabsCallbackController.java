package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.service.BitLabsCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/callbacks/bitlabs")
public class BitLabsCallbackController {

    private final BitLabsCallbackService callbackService;

    @GetMapping
    public ResponseEntity<String> callback(HttpServletRequest request) {
        callbackService.process(request.getQueryString());
        return ResponseEntity.ok("OK");
    }
}
