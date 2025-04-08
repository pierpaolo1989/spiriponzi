package com.soa.spiriponzi.repository;

import com.soa.spiriponzi.model.UserAd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAdRepository extends JpaRepository<UserAd, Long> {

	List<UserAd> findByUserIdAndAdActiveIsTrue(Long userId);
	List<UserAd> findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long userId);
}
