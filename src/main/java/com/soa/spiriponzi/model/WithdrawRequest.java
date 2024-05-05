package com.soa.spiriponzi.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name="withdraw_request")
public class WithdrawRequest {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
    @Column(nullable=false)
	private LocalDateTime dateRequest;
    @Column(nullable=false)
	private String status;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column
    private double amount;
    @Column
    private String transactionId;
    @Column
    private String currency;

}
