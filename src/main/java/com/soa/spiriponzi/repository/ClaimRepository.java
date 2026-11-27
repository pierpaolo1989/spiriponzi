package com.soa.spiriponzi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.soa.spiriponzi.model.Claim;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ClaimRepository  extends JpaRepository<Claim, Long> {

	List<Claim> findByUserEmail(String email);
	List<Claim> findTop10ByUserEmailOrderByDateRequestDesc(String email);
	List<Claim> findByUserEmailAndDateRequestBetweenOrderByDateRequestAsc(
			String email, LocalDateTime start, LocalDateTime end);
	boolean existsByExternalTransactionId(String externalTransactionId);
	Optional<Claim> findByExternalTransactionId(String externalTransactionId);

	@Query("select coalesce(sum(c.amount), 0) from Claim c")
	double sumClaimAmounts();
}
