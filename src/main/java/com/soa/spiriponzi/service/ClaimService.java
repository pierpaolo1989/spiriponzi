package com.soa.spiriponzi.service;

import java.util.List;

import com.soa.spiriponzi.dto.ClaimDto;
import com.soa.spiriponzi.dto.UserStatsDto;

public interface ClaimService {
	
	ClaimDto startClaim(String email);
	long getClaimCooldownSeconds(String email);
	long getGameClaimCooldownSeconds(String email);
	UserStatsDto startGameClaim(String email);
	List<ClaimDto> findByUser(String email);

}
