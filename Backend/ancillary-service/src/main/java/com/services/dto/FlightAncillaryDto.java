package com.services.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightAncillaryDto {
    private Long id;
    private Long flightId;
    private AncillaryDto ancillary;
    private BigDecimal price;
    // Boolean, not boolean - see SeatDto/FareRulesDto for why a primitive breaks partial requests.
    private Boolean available;
}
