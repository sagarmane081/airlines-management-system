package com.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaggagePolicyDto {
    private Long id;
    private BigDecimal cabinBaggageAllowanceKg;
    private BigDecimal checkedBaggageAllowanceKg;
    private Integer checkedBaggagePieces;
    private BigDecimal extraBaggageFeePerKg;
}
