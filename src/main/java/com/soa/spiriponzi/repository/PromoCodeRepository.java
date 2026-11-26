package com.soa.spiriponzi.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soa.spiriponzi.model.PromoCode;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    boolean existsByCode(String code);

    List<PromoCode> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select promoCode from PromoCode promoCode where promoCode.code = :code")
    Optional<PromoCode> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select promoCode from PromoCode promoCode where promoCode.id = :id")
    Optional<PromoCode> findByIdForUpdate(@Param("id") Long id);
}
