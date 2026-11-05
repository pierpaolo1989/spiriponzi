package com.soa.spiriponzi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.soa.spiriponzi.model.WithdrawRequest;

import java.util.List;

public interface WithdrawRepository extends JpaRepository<WithdrawRequest, Long> {
	
	Page<WithdrawRequest> findByUserEmail(String email, Pageable pageable);
	long countByStatus(String status);
	List<WithdrawRequest> findTop10ByOrderByDateRequestDesc();
}
