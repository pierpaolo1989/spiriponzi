package com.soa.spiriponzi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soa.spiriponzi.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    Optional<User> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);
}