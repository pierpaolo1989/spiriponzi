package com.soa.spiriponzi.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ADDto {
    private Long id;
    private String title;
    private double reward;
    private int timer;
    private String url;
}
