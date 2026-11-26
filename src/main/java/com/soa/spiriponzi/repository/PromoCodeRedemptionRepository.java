package com.soa.spiriponzi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soa.spiriponzi.model.PromoCodeRedemption;

public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, Long> {
    boolean existsByPromoCode_IdAndUser_Id(Long promoCodeId, Long userId);
}
