package com.soa.spiriponzi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SearchResultDto {
    private String title;
    private String description;
    private String category;
    private String url;
}
