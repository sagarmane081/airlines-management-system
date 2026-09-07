package com.services.service;

import com.services.client.FlightClient;
import com.services.dto.FlightAncillaryDto;
import com.services.dto.FlightOwnerView;
import com.services.entity.Ancillary;
import com.services.entity.FlightAncillary;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.FlightAncillaryMapper;
import com.services.repository.AncillaryRepository;
import com.services.repository.FlightAncillaryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightAncillaryService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final FlightAncillaryRepository flightAncillaryRepository;
    private final AncillaryRepository ancillaryRepository;
    private final FlightClient flightClient;

    public FlightAncillaryService(FlightAncillaryRepository flightAncillaryRepository, AncillaryRepository ancillaryRepository,
                                   FlightClient flightClient) {
        this.flightAncillaryRepository = flightAncillaryRepository;
        this.ancillaryRepository = ancillaryRepository;
        this.flightClient = flightClient;
    }

    public FlightAncillaryDto createFlightAncillary(FlightAncillaryDto dto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create flight ancillaries");
        }
        Long ancillaryId = dto.getAncillary() != null ? dto.getAncillary().getId() : null;
        Ancillary ancillary = ancillaryRepository.findById(ancillaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ancillary not found with id: " + ancillaryId));

        FlightOwnerView flight = flightClient.getFlightById(dto.getFlightId());
        AirlineOwnershipChecker.requireAirlineOwnership(flight.getAirline(), requesterId, requesterRole);
        requireSameAirline(flight, ancillary);

        FlightAncillary saved = flightAncillaryRepository.save(FlightAncillaryMapper.toEntity(dto, ancillary));
        return FlightAncillaryMapper.toDto(saved);
    }

    /**
     * A flight's airline can only attach its own ancillary catalog items to its own flights - not
     * a competitor's. This is a data-consistency rule, distinct from the ownership check above:
     * even an admin (who bypasses ownership) shouldn't be allowed to cross-wire two different
     * airlines' catalogs, so this check applies unconditionally, not just to owners.
     */
    private void requireSameAirline(FlightOwnerView flight, Ancillary ancillary) {
        if (!flight.getAirline().getId().equals(ancillary.getAirlineId())) {
            throw new ForbiddenException(
                    "Ancillary " + ancillary.getId() + " belongs to a different airline than flight " + flight.getId());
        }
    }

    public FlightAncillaryDto getFlightAncillaryById(Long id) {
        FlightAncillary flightAncillary = flightAncillaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightAncillary not found with id: " + id));
        return FlightAncillaryMapper.toDto(flightAncillary);
    }

    public List<FlightAncillaryDto> getAllFlightAncillaries() {
        return flightAncillaryRepository.findAll().stream().map(FlightAncillaryMapper::toDto).toList();
    }
}
