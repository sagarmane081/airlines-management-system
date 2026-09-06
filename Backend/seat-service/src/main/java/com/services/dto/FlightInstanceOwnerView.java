package com.services.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A narrow projection of flight-ops-service's GET /api/flight-instances/{id} response - only
 * the nested chain needed to reach the owning airline. Spring's default Jackson config ignores
 * the extra JSON fields (departureTime, arrivalTime, status, aircraft) the real response carries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightInstanceOwnerView {
    private Long id;
    private FlightOwnerView flight;
}
