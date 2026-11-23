package com.soa.spiriponzi.repository;

import java.util.Optional;

import com.soa.spiriponzi.model.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationToken token where token.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findWithLockByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("delete from EmailVerificationToken token where token.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
