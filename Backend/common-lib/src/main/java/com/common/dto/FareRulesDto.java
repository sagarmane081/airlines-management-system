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
    // Boolean, not boolean - Jackson can select the Lombok all-args constructor as a creator for
    // partial JSON objects, and passing null into a primitive constructor slot throws instead of
    // defaulting. A wrapper type tolerates the field being omitted from a request.
    private Boolean refundable;
    private Boolean changeable;
    private BigDecimal cancellationFee;
    private BigDecimal changeFee;
    private Integer refundDeadlineHours;
    private Integer changeDeadlineHours;
}
