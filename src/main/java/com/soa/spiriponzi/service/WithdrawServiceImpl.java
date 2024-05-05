package com.soa.spiriponzi.service;


import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.WithdrawRequest;
import com.soa.spiriponzi.repository.WithdrawRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WithdrawServiceImpl implements WithdrawService {

	private final WithdrawRepository withdrawRepository;

	@Override
	public List<WithdrawDto> findByUser(String email, int page, int size) {
		final Pageable pageable = PageRequest.of(page, size);
		Page<WithdrawRequest> requests = withdrawRepository.findByUserEmail(email, pageable);
		return requests.getContent().stream().map(el -> {
			WithdrawDto dto = WithdrawDto.builder()
					.amount(el.getAmount())
					.dateRequest(el.getDateRequest())
					.transactionId(el.getTransactionId())
					.id(el.getId())
					.build();
			return dto;
		}).collect(Collectors.toList());
	}

	@Override
	public void startRequest(String email, WithdrawDto request) {
		var entity = new WithdrawRequest();
		entity.setAmount(request.getAmount());
		entity.setDateRequest(LocalDateTime.now());
		entity.setStatus("PENDING");
		User user = new User();
		user.setEmail(email);
		entity.setUser(user);
		entity.setCurrency(request.getCurrency());
		withdrawRepository.save(entity);
		
		//Coinbase integration 
	}


}