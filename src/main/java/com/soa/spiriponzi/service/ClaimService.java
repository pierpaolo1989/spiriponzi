package com.soa.spiriponzi.service;

import java.util.List;

import com.soa.spiriponzi.dto.ClaimDto;

public interface ClaimService {
	
	void startClaim(String email);
	List<ClaimDto> findByUser(String email);

}
