package com.services.service;

import com.common.dto.AircraftDto;
import com.services.client.AircraftClient;
import com.services.dto.SeatDto;
import com.services.entity.CabinClass;
import com.services.entity.Seat;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.SeatMapper;
import com.services.repository.CabinClassRepository;
import com.services.repository.SeatRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final SeatRepository seatRepository;
    private final CabinClassRepository cabinClassRepository;
    private final AircraftClient aircraftClient;

    public SeatService(SeatRepository seatRepository, CabinClassRepository cabinClassRepository,
                        AircraftClient aircraftClient) {
        this.seatRepository = seatRepository;
        this.cabinClassRepository = cabinClassRepository;
        this.aircraftClient = aircraftClient;
    }

    public SeatDto createSeat(SeatDto seatDto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create seats");
        }
        Long cabinClassId = seatDto.getCabinClass() != null ? seatDto.getCabinClass().getId() : null;
        CabinClass cabinClass = cabinClassRepository.findById(cabinClassId)
                .orElseThrow(() -> new ResourceNotFoundException("CabinClass not found with id: " + cabinClassId));

        AircraftDto aircraft = aircraftClient.getAircraftById(cabinClass.getSeatMap().getAircraftId());
        AirlineOwnershipChecker.requireAirlineOwnership(aircraft.getAirline(), requesterId, requesterRole);

        Seat saved = seatRepository.save(SeatMapper.toEntity(seatDto, cabinClass));
        return SeatMapper.toDto(saved);
    }

    public SeatDto getSeatById(Long id) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + id));
        return SeatMapper.toDto(seat);
    }

    public List<SeatDto> getAllSeats() {
        return seatRepository.findAll().stream().map(SeatMapper::toDto).toList();
    }
}
