package com.services.dto;

import com.common.dto.AirlineDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A narrow projection of flight-ops-service's GET /api/flights/{id} response - only the fields
 * needed to check airline ownership. Spring's default Jackson config ignores the extra JSON
 * fields (flightNumber, departureAirport, arrivalAirport) that the real response also carries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightOwnerView {
    private Long id;
    private AirlineDto airline;
}
