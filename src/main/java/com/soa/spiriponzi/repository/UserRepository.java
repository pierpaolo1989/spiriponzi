package com.soa.spiriponzi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soa.spiriponzi.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
}