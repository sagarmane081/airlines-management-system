package com.services.service;

import com.common.dto.AirlineDto;
import com.common.dto.FareDto;
import com.services.client.FlightClient;
import com.services.dto.FlightOwnerView;
import com.services.entity.BaggagePolicy;
import com.services.entity.Fare;
import com.services.entity.FareRules;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.FareMapper;
import com.services.repository.FareRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FareService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final FareRepository fareRepository;
    private final FlightClient flightClient;

    public FareService(FareRepository fareRepository, FlightClient flightClient) {
        this.fareRepository = fareRepository;
        this.flightClient = flightClient;
    }

    public FareDto createFare(FareDto fareDto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create fares");
        }
        FlightOwnerView flight = flightClient.getFlightById(fareDto.getFlightId());
        requireAirlineOwnership(flight.getAirline(), requesterId, requesterRole);

        Fare fare = FareMapper.toEntity(fareDto);

        if (fareDto.getFareRules() != null) {
            FareRules fareRules = FareMapper.toEntity(fareDto.getFareRules());
            fareRules.setFare(fare);
            fare.setFareRules(fareRules);
        }
        if (fareDto.getBaggagePolicy() != null) {
            BaggagePolicy baggagePolicy = FareMapper.toEntity(fareDto.getBaggagePolicy());
            baggagePolicy.setFare(fare);
            fare.setBaggagePolicy(baggagePolicy);
        }

        Fare saved = fareRepository.save(fare);
        return FareMapper.toDto(saved);
    }

    private void requireAirlineOwnership(AirlineDto airline, Long requesterId, String requesterRole) {
        if (ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            return;
        }
        boolean isOwner = airline.getOwnerId() != null && airline.getOwnerId().equals(requesterId);
        if (!isOwner) {
            throw new ForbiddenException("Airline " + airline.getId() + " is not owned by the requesting user");
        }
    }

    public FareDto getFareById(Long id) {
        Fare fare = fareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fare not found with id: " + id));
        return FareMapper.toDto(fare);
    }

    public List<FareDto> getAllFares() {
        return fareRepository.findAll().stream().map(FareMapper::toDto).toList();
    }
}
