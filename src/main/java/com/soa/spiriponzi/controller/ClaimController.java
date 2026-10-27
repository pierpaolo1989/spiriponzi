package com.soa.spiriponzi.controller;

import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soa.spiriponzi.dto.ClaimDto;
import com.soa.spiriponzi.dto.UserStatsDto;
import com.soa.spiriponzi.service.CaptchaService;
import com.soa.spiriponzi.service.ClaimService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/claims")
public class ClaimController {
	
    private final CaptchaService captchaService;
    private final ClaimService claimService;

    @PostMapping
    public ResponseEntity<Void> startClaim(HttpServletRequest request) {
        String response = request.getParameter("g-recaptcha-response");
        captchaService.processResponse(response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
        	claimService.startClaim(authentication.getName());
		} catch (Exception e) {
			return new ResponseEntity<>(HttpStatusCode.valueOf(400));
		}
        
        return ResponseEntity.ok(null);
    }

    @GetMapping("/game/status")
    public long getGameClaimCooldownSeconds() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return claimService.getGameClaimCooldownSeconds(authentication.getName());
    }

    @PostMapping("/game")
    public UserStatsDto startGameClaim() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return claimService.startGameClaim(authentication.getName());
    }
    
    @GetMapping
    public ResponseEntity<List<ClaimDto>> getClaims() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<ClaimDto> claims = claimService.findByUser(authentication.getName());
        return ResponseEntity.ok(claims);
    }

	
}
