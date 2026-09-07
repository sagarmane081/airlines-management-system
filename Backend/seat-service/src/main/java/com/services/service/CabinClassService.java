package com.services.service;

import com.common.dto.AircraftDto;
import com.services.client.AircraftClient;
import com.services.dto.CabinClassDto;
import com.services.entity.CabinClass;
import com.services.entity.SeatMap;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.CabinClassMapper;
import com.services.repository.CabinClassRepository;
import com.services.repository.SeatMapRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CabinClassService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final CabinClassRepository cabinClassRepository;
    private final SeatMapRepository seatMapRepository;
    private final AircraftClient aircraftClient;

    public CabinClassService(CabinClassRepository cabinClassRepository, SeatMapRepository seatMapRepository,
                              AircraftClient aircraftClient) {
        this.cabinClassRepository = cabinClassRepository;
        this.seatMapRepository = seatMapRepository;
        this.aircraftClient = aircraftClient;
    }

    public CabinClassDto createCabinClass(CabinClassDto cabinClassDto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create cabin classes");
        }
        Long seatMapId = cabinClassDto.getSeatMap() != null ? cabinClassDto.getSeatMap().getId() : null;
        SeatMap seatMap = seatMapRepository.findById(seatMapId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatMap not found with id: " + seatMapId));

        AircraftDto aircraft = aircraftClient.getAircraftById(seatMap.getAircraftId());
        AirlineOwnershipChecker.requireAirlineOwnership(aircraft.getAirline(), requesterId, requesterRole);

        CabinClass saved = cabinClassRepository.save(CabinClassMapper.toEntity(cabinClassDto, seatMap));
        return CabinClassMapper.toDto(saved);
    }

    public CabinClassDto getCabinClassById(Long id) {
        CabinClass cabinClass = cabinClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CabinClass not found with id: " + id));
        return CabinClassMapper.toDto(cabinClass);
    }

    public List<CabinClassDto> getAllCabinClasses() {
        return cabinClassRepository.findAll().stream().map(CabinClassMapper::toDto).toList();
    }
}
