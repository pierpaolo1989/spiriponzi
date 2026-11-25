package com.soa.spiriponzi.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "affiliate_clicks", indexes = {
        @Index(name = "idx_affiliate_click_user_visitor_time",
                columnList = "affiliate_user_id, visitor_hash, clicked_at"),
        @Index(name = "idx_affiliate_click_user", columnList = "affiliate_user_id")
})
public class AffiliateClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "affiliate_user_id", nullable = false)
    private User affiliateUser;

    @Column(name = "visitor_hash", nullable = false, length = 64)
    private String visitorHash;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;
}
