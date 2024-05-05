package com.soa.spiriponzi.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soa.spiriponzi.model.UserStats;

public interface UserStatsRepository  extends JpaRepository<UserStats, Long> {

	Optional<UserStats> findByUserEmail(String email);
	
}
