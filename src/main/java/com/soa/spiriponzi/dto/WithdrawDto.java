package com.soa.spiriponzi.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawDto {
	
	private Long id;
	private LocalDateTime dateRequest;
	private String status;
	@NotNull
    private double amount;
    private String transactionId;
    @NotNull
    private String currency;

}
