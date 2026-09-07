package com.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FareRulesDto {
    private Long id;
    private boolean refundable;
    private boolean changeable;
    private BigDecimal cancellationFee;
    private BigDecimal changeFee;
    private Integer refundDeadlineHours;
    private Integer changeDeadlineHours;
}
