package com.soa.spiriponzi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateUserDto {
	
    private String ethWallet;
    private String btcWallet;
    @Email(message = "Enter a valid PayPal email address")
    @Size(max = 254, message = "PayPal email must be at most 254 characters")
    private String paypalEmail;
    @NotEmpty(message = "Email should not be empty")
    @Email
    private String email;

}
