package com.soa.spiriponzi.repository;

import com.soa.spiriponzi.model.Ad;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdRepository extends JpaRepository<Ad, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Ad a WHERE a.id = :id")
	Optional<Ad> findWithLockById(@Param("id") Long id);

	@Query("""
    SELECT a,
           CASE
               WHEN EXISTS (
                   SELECT ua FROM UserAd ua
                   WHERE ua.ad = a
                   AND ua.user.id = :userId
                   AND FUNCTION('DATE', ua.completedAt) = FUNCTION('DATE', CURRENT_DATE)
               )
               THEN true
               ELSE false
           END AS completed
    FROM Ad a
    WHERE a.proposer IS NULL
       OR (a.active = true AND a.proposer.id <> :userId AND a.deleted = false)
    """)
	List<Object[]> findAdsWithCompletionStatus(@Param("userId") Long userId);
	List<Ad> findByActiveTrue();
	List<Ad> findByProposerIdAndDeletedIsFalse(Long userId);
}
