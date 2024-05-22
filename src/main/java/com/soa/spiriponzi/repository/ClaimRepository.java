package com.soa.spiriponzi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soa.spiriponzi.model.Claim;

public interface ClaimRepository  extends JpaRepository<Claim, Long> {

	List<Claim> findByUserEmail(String email);
	List<Claim> findTop10ByUserEmailOrderByDateRequestDesc(String email);
}
