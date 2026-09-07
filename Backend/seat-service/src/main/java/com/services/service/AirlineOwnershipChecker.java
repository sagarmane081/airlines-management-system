package com.services.service;

import com.common.dto.AirlineDto;
import com.services.exception.ForbiddenException;

/**
 * Shared by SeatMapService/CabinClassService/SeatService, which all gate creation the same way:
 * a ROLE_AIRLINE_OWNER may only manage catalog data belonging to their own airline, resolved by
 * walking each entity's chain up to its owning aircraft/airline. Kept local to seat-service
 * (a plain static utility, not a common-lib class) - same reasoning as every other duplicated
 * per-service authorization helper in this codebase, just shared across this service's own
 * sibling classes rather than copy-pasted three times within one module.
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
