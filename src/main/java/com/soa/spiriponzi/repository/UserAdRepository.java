package com.soa.spiriponzi.repository;

import com.soa.spiriponzi.model.UserAd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;

public interface UserAdRepository extends JpaRepository<UserAd, Long> {

	List<UserAd> findByUserIdAndAdActiveIsTrue(Long userId);
	List<UserAd> findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long userId);
	boolean existsByAdIdAndUserIdAndCompletedAt(Long adId, Long userId, LocalDate completedAt);
}
