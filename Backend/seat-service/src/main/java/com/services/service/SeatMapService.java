package com.services.service;

import com.common.dto.AircraftDto;
import com.services.client.AircraftClient;
import com.services.dto.SeatMapDto;
import com.services.entity.SeatMap;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.SeatMapMapper;
import com.services.repository.SeatMapRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatMapService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final SeatMapRepository seatMapRepository;
    private final AircraftClient aircraftClient;

    public SeatMapService(SeatMapRepository seatMapRepository, AircraftClient aircraftClient) {
        this.seatMapRepository = seatMapRepository;
        this.aircraftClient = aircraftClient;
    }

    public SeatMapDto createSeatMap(SeatMapDto seatMapDto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create seat maps");
        }
        AircraftDto aircraft = aircraftClient.getAircraftById(seatMapDto.getAircraftId());
        AirlineOwnershipChecker.requireAirlineOwnership(aircraft.getAirline(), requesterId, requesterRole);

        SeatMap saved = seatMapRepository.save(SeatMapMapper.toEntity(seatMapDto));
        return SeatMapMapper.toDto(saved);
    }

    public SeatMapDto getSeatMapById(Long id) {
        SeatMap seatMap = seatMapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SeatMap not found with id: " + id));
        return SeatMapMapper.toDto(seatMap);
    }

    public List<SeatMapDto> getAllSeatMaps() {
        return seatMapRepository.findAll().stream().map(SeatMapMapper::toDto).toList();
    }
}
