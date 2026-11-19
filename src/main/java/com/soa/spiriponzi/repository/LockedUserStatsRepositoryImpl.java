package com.soa.spiriponzi.repository;

import java.util.Optional;

import com.soa.spiriponzi.model.UserStats;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LockedUserStatsRepositoryImpl implements LockedUserStatsRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<UserStats> findWithLockByUserEmail(String email) {
        return entityManager.createQuery(
                        "select s from UserStats s where s.id = "
                                + "(select u.userStats.id from User u where u.email = :email)", UserStats.class)
                .setParameter("email", email)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList().stream().findFirst()
                .map(stats -> {
                    // The user may have loaded these stats before the lock was acquired.
                    entityManager.refresh(stats, LockModeType.PESSIMISTIC_WRITE);
                    return stats;
                });
    }
}
