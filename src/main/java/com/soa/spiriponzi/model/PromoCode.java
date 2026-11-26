package com.soa.spiriponzi.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promo_codes", uniqueConstraints = @UniqueConstraint(
        name = "uk_promo_codes_code", columnNames = "code"))
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String code;

    @Column(nullable = false)
    private int points;

    @Column(name = "max_redemptions", nullable = false)
    private int maxRedemptions;

    @Column(name = "redemptions_used", nullable = false)
    private int redemptionsUsed;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
