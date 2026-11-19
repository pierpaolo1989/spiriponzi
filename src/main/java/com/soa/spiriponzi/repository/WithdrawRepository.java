package com.soa.spiriponzi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.soa.spiriponzi.model.WithdrawRequest;

import java.util.List;
import java.util.Optional;

public interface WithdrawRepository extends JpaRepository<WithdrawRequest, Long> {
	
	Page<WithdrawRequest> findByUserEmail(String email, Pageable pageable);
	long countByStatus(String status);
	List<WithdrawRequest> findTop10ByOrderByDateRequestDesc();
	Page<WithdrawRequest> findByStatusInOrderByDateRequestAsc(List<String> statuses, Pageable pageable);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<WithdrawRequest> findWithLockById(Long id);
}
