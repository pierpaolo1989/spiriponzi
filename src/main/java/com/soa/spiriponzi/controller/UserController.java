package com.soa.spiriponzi.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soa.spiriponzi.dto.UserStatsDto;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.service.UserService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/users/stats")
public class UserController {
	
	private final UserService userService;
	
	@GetMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<UserStatsDto> getUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final UserStats user = userService.findStatsByEmail(authentication.getName());
		final UserStatsDto result = UserStatsDto.builder()
				.annualPoints(user.getAnnualPoints())
				.monthlyPoints(user.getMonthlyPoints())
				.numberOfClaims(user.getNumberOfClaims())
				.withdrawals(user.getWithdrawals())
				.lastRequest(user.getLastRequest())
				.build();
		return ResponseEntity.ok(result);
	}

}
