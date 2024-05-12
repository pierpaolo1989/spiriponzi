package com.soa.spiriponzi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateUserDto {
	
    private String ethWallet;
    private String btcWallet;
    @NotEmpty(message = "Email should not be empty")
    @Email
    private String email;

}
