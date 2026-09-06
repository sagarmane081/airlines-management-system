package com.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FareDto {
    private Long id;
    private Long flightId;
    private String cabinClass;
    private BigDecimal price;
    private String currency;
}
