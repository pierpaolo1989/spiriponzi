package com.soa.spiriponzi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportTicketForm {

    @NotBlank
    @Size(max = 40)
    private String topic;

    @NotBlank
    @Size(max = 5000)
    private String message;
}
