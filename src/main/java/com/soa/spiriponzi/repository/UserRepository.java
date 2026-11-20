package com.soa.spiriponzi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import com.soa.spiriponzi.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(Long id);
    List<User> findTop5ByOrderByIdDesc();

    @Query("select coalesce(sum(stats.annualPoints), 0) from User u left join u.userStats stats")
    long sumAnnualPoints();

    Optional<User> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);
}