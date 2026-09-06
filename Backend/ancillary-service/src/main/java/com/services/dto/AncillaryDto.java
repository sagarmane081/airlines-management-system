package com.services.dto;

import com.services.entity.AncillaryType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AncillaryDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private AncillaryType type;
}
