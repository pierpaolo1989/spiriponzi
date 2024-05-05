package com.soa.spiriponzi.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClaimDto {

	private Long id;
	private double amount;
	private LocalDateTime date;
}
