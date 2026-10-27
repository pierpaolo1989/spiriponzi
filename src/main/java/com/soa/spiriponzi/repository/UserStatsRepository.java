package com.soa.spiriponzi.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.soa.spiriponzi.model.UserStats;
import jakarta.persistence.LockModeType;

public interface UserStatsRepository  extends JpaRepository<UserStats, Long> {

	Optional<UserStats> findByUserEmail(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<UserStats> findWithLockByUserEmail(String email);
	
}
