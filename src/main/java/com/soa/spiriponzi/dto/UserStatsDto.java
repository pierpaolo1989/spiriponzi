package com.soa.spiriponzi.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStatsDto {
	
	private int monthlyPoints;
	private int annualPoints;
	private int numberOfClaims;
	private int withdrawals;
	private LocalDateTime lastRequest;

}
