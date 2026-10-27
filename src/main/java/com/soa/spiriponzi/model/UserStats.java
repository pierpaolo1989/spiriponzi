package com.soa.spiriponzi.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
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
@Table(name="users_stats")
public class UserStats {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
    @Column
	private int monthlyPoints;
    @Column
	private int annualPoints;
    @Column
	private int numberOfClaims;
    @Column
	private int withdrawals;
    @Column
    private LocalDateTime lastRequest;
    @Column
    private LocalDateTime lastGameClaimAt;
    @OneToOne(mappedBy = "userStats")
    private User user;

}
