package com.services.service;

import com.common.dto.AirlineDto;
import com.services.exception.ForbiddenException;

/**
 * Shared by AncillaryService/MealService/FlightAncillaryService/FlightMealService, which all gate
 * creation the same way: a ROLE_AIRLINE_OWNER may only manage their own airline's catalog data.
 * Kept local to ancillary-service (a plain static utility, not a common-lib class) - same
 * reasoning as the identically-named classes in seat-service and flight-ops-service.
 */
public class AirlineOwnershipChecker {

    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private AirlineOwnershipChecker() {
    }

    public static void requireAirlineOwnership(AirlineDto airline, Long requesterId, String requesterRole) {
        if (ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            return;
        }
        boolean isOwner = airline.getOwnerId() != null && airline.getOwnerId().equals(requesterId);
        if (!isOwner) {
            throw new ForbiddenException("Airline " + airline.getId() + " is not owned by the requesting user");
        }
    }
}
