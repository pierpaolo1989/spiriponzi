package com.soa.spiriponzi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoverPasswordDto {
	
	@NotBlank
	@Email
	private String email;
	@NotBlank
	private String confirmEmail;

}
