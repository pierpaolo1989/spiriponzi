package com.soa.spiriponzi.service;


import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.exception.WithdrawalException;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.WithdrawRequest;
import com.soa.spiriponzi.model.enums.WithdrawStatus;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.utils.Constants;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WithdrawServiceImpl implements WithdrawService {

	private final WithdrawRepository withdrawRepository;
	private final UserRepository userRepository;
	private final PaymentService paymentService;

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
	@Transactional(propagation = Propagation.REQUIRED)
	public void startRequest(String email, WithdrawDto request) {
		User user = userRepository.findByEmail(email);
		if (user.getUserStats().getAnnualPoints() < Constants.LIMIT_WITHDRAWAL) {
			throw new WithdrawalException();
		}
		var entity = new WithdrawRequest();
		entity.setAmount(request.getAmount());
		entity.setDateRequest(LocalDateTime.now());
		entity.setStatus(WithdrawStatus.PENDING.name());
		entity.setUser(user);
		entity.setCurrency(request.getCurrency());
		withdrawRepository.save(entity);
		
		//Bitcoin org integration 
		paymentService.createTransaction(user.getBtcWallet(), Double.valueOf(request.getAmount()).longValue());
	}


}