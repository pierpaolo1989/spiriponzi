package com.soa.spiriponzi.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.service.WithdrawService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/withdraws")
public class WihdrawController {
	
	private final WithdrawService withdrawService;
	
	@GetMapping
	public List<WithdrawDto> findMenuByUser(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
											@RequestParam(value = "size", required = false, defaultValue = "20") int size) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return withdrawService.findByUser(authentication.getName(), page, size);
	}
	
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> startWithdrawRequest(@RequestBody WithdrawDto request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		withdrawService.startRequest(authentication.getName(), request);
		return ResponseEntity.ok(null);
	}

	
}
