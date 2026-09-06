package com.services.service;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.services.entity.Aircraft;
import com.services.entity.Airline;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.AircraftMapper;
import com.services.repository.AircraftRepository;
import com.services.repository.AirlineRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AircraftService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final AircraftRepository aircraftRepository;
    private final AirlineRepository airlineRepository;
    private final AirlineService airlineService;

    public AircraftService(AircraftRepository aircraftRepository, AirlineRepository airlineRepository,
                            AirlineService airlineService) {
        this.aircraftRepository = aircraftRepository;
        this.airlineRepository = airlineRepository;
        this.airlineService = airlineService;
    }

    public AircraftDto createAircraft(AircraftDto aircraftDto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create aircraft");
        }
        Long airlineId = aircraftDto.getAirline() != null ? aircraftDto.getAirline().getId() : null;
        Airline airline = airlineRepository.findById(airlineId)
                .orElseThrow(() -> new ResourceNotFoundException("Airline not found with id: " + airlineId));
        requireAirlineOwnership(airline, requesterId, requesterRole);

        Aircraft saved = aircraftRepository.save(AircraftMapper.toEntity(aircraftDto, airline));
        AirlineDto airlineDto = airlineService.getAirlineById(saved.getAirline().getId());
        return AircraftMapper.toDto(saved, airlineDto);
    }

    /**
     * A ROLE_AIRLINE_OWNER may only manage their own airline's fleet - ROLE_SYSTEM_ADMIN bypasses
     * this since admins manage any airline. Airline is fetched locally (same service, same DB), so
     * no Feign call is needed here, unlike the equivalent check in other services.
     */
    private void requireAirlineOwnership(Airline airline, Long requesterId, String requesterRole) {
        if (ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            return;
        }
        boolean isOwner = airline.getOwnerId() != null && airline.getOwnerId().equals(requesterId);
        if (!isOwner) {
            throw new ForbiddenException("Airline " + airline.getId() + " is not owned by the requesting user");
        }
    }

    public AircraftDto getAircraftById(Long id) {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found with id: " + id));
        AirlineDto airlineDto = airlineService.getAirlineById(aircraft.getAirline().getId());
        return AircraftMapper.toDto(aircraft, airlineDto);
    }

    public List<AircraftDto> getAllAircraft() {
        return enrichAircraft(aircraftRepository.findAll());
    }

    public List<AircraftDto> getAircraftByIds(List<Long> ids) {
        return enrichAircraft(aircraftRepository.findAllByIdIn(ids));
    }

    private List<AircraftDto> enrichAircraft(List<Aircraft> aircraftList) {
        if (aircraftList.isEmpty()) {
            return List.of();
        }

        List<Long> airlineIds = aircraftList.stream()
                .map(a -> a.getAirline().getId())
                .distinct()
                .toList();
        Map<Long, AirlineDto> airlinesById = airlineService.getAirlinesByIds(airlineIds).stream()
                .collect(Collectors.toMap(AirlineDto::getId, Function.identity()));

        return aircraftList.stream()
                .map(a -> AircraftMapper.toDto(a, airlinesById.get(a.getAirline().getId())))
                .toList();
    }
}
