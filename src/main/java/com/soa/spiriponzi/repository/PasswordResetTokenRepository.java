package com.soa.spiriponzi.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soa.spiriponzi.model.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash")
	Optional<PasswordResetToken> findWithLockByTokenHash(@Param("tokenHash") String tokenHash);

	@Modifying
	@Query("delete from PasswordResetToken token where token.user.id = :userId")
	void deleteAllByUserId(@Param("userId") Long userId);
}
