package com.services.dto;

import com.common.dto.AirlineDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A narrow projection of flight-ops-service's GET /api/flights/{id} response - only the fields
 * needed to check airline ownership. Spring's default Jackson config ignores the extra JSON
 * fields (flightNumber, departureAirport, arrivalAirport) the real response also carries. Same
 * pattern as pricing-service's/seat-service's identically-named class.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightOwnerView {
    private Long id;
    private AirlineDto airline;
}
