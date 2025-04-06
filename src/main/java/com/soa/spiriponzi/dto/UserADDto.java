package com.soa.spiriponzi.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserADDto extends ADDto {
    private LocalDate completedAt;
}
