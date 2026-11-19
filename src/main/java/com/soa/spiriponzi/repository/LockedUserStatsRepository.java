package com.soa.spiriponzi.repository;

import java.util.Optional;

import com.soa.spiriponzi.model.UserStats;

public interface LockedUserStatsRepository {
    Optional<UserStats> findWithLockByUserEmail(String email);
}
