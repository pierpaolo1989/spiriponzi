package com.soa.spiriponzi.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soa.spiriponzi.model.AffiliateClick;

public interface AffiliateClickRepository extends JpaRepository<AffiliateClick, Long> {

    boolean existsByAffiliateUser_IdAndVisitorHashAndClickedAtAfter(
            Long affiliateUserId, String visitorHash, LocalDateTime clickedAt);

    long countByAffiliateUser_Id(Long affiliateUserId);
}
