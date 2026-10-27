package com.soa.spiriponzi.service;

import java.util.List;

import com.soa.spiriponzi.dto.ClaimDto;
import com.soa.spiriponzi.dto.UserStatsDto;

public interface ClaimService {
	
	void startClaim(String email);
	long getGameClaimCooldownSeconds(String email);
	UserStatsDto startGameClaim(String email);
	List<ClaimDto> findByUser(String email);

}
