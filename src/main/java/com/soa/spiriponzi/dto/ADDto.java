package com.soa.spiriponzi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ADDto {
    private Long id;
    private String title;
    private double reward;
    private double budget;
    private int timer;
    private String url;
    private LocalDate createdAt;
    private boolean completed;
    private boolean active;
}
