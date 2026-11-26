package com.soa.spiriponzi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soa.spiriponzi.model.PromoCodeRedemption;

public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, Long> {
    boolean existsByPromoCode_IdAndUser_Id(Long promoCodeId, Long userId);

    @Modifying
    @Query("delete from PromoCodeRedemption redemption where redemption.promoCode.id = :promoCodeId")
    void deleteAllByPromoCodeId(@Param("promoCodeId") Long promoCodeId);
}
