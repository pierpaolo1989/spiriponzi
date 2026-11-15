package com.soa.spiriponzi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordFormDto {

	@NotBlank
	private String token;

	@NotBlank
	private String password;

	@NotBlank
	private String confirmPassword;
}
