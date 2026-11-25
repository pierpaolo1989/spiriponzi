package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.service.CherriesCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/callbacks/cherries")
public class CherriesCallbackController {

    private final CherriesCallbackService callbackService;

    @GetMapping
    public ResponseEntity<String> getCallback(HttpServletRequest request) {
        callbackService.process(request.getQueryString());
        return ResponseEntity.ok("OK");
    }

    @PostMapping
    public ResponseEntity<String> postCallback(HttpServletRequest request) {
        String rawPayload = request.getQueryString();
        if (rawPayload == null || rawPayload.isBlank()) {
            try {
                rawPayload = request.getReader().lines().collect(Collectors.joining());
            } catch (IOException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unable to read Cherries callback payload", exception);
            }
        }
        callbackService.process(rawPayload);
        return ResponseEntity.ok("OK");
    }
}
