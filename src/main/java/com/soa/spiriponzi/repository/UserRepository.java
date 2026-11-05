package com.soa.spiriponzi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.soa.spiriponzi.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    List<User> findTop5ByOrderByIdDesc();

    @Query("select coalesce(sum(u.balance), 0) from User u")
    double sumBalances();

    Optional<User> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);
}