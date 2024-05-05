package com.soa.spiriponzi.service;

import java.util.List;

import com.soa.spiriponzi.dto.WithdrawDto;

public interface WithdrawService {
	
	List<WithdrawDto> findByUser(String email, int page, int size);
	void startRequest(String email, WithdrawDto request);

}
