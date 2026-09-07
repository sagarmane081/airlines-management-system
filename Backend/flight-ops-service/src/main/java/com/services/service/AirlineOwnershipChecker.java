package com.services.service;

import com.common.dto.AirlineDto;
import com.services.exception.ForbiddenException;

/**
 * Shared by FlightService and FlightScheduleService, which both gate creation the same way: a
 * ROLE_AIRLINE_OWNER may only manage their own airline's flights/schedules. Kept local to
 * flight-ops-service (a plain static utility, not a common-lib class) - same reasoning as
 * seat-service's identically-named class: shared across this service's own sibling classes rather
 * than copy-pasted.
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
