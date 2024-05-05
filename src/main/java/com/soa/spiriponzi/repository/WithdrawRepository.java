package com.soa.spiriponzi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.soa.spiriponzi.model.WithdrawRequest;

public interface WithdrawRepository extends JpaRepository<WithdrawRequest, Long> {
	
	Page<WithdrawRequest> findByUserEmail(String email, Pageable pageable);
}
