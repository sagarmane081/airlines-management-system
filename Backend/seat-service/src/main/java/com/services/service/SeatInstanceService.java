package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.FlightClient;
import com.services.dto.FlightInstanceOwnerView;
import com.services.dto.SeatInstanceDto;
import com.services.entity.SeatInstance;
import com.services.entity.SeatStatus;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.exception.SeatAlreadyBookedException;
import com.services.exception.SeatNotAvailableException;
import com.services.mapper.SeatInstanceMapper;
import com.services.repository.SeatInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatInstanceService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final SeatInstanceRepository seatInstanceRepository;
    private final FlightClient flightClient;

    public SeatInstanceService(SeatInstanceRepository seatInstanceRepository, FlightClient flightClient) {
        this.seatInstanceRepository = seatInstanceRepository;
        this.flightClient = flightClient;
    }

    public SeatInstanceDto createSeatInstance(SeatInstanceDto seatInstanceDto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create seat instances");
        }
        FlightInstanceOwnerView flightInstance = flightClient.getFlightInstanceById(seatInstanceDto.getFlightInstanceId());
        requireAirlineOwnership(flightInstance.getFlight().getAirline(), requesterId, requesterRole);

        SeatInstance saved = seatInstanceRepository.save(SeatInstanceMapper.toEntity(seatInstanceDto));
        return SeatInstanceMapper.toDto(saved);
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

    public SeatInstanceDto getSeatInstanceById(Long id) {
        SeatInstance seatInstance = seatInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SeatInstance not found with id: " + id));
        return SeatInstanceMapper.toDto(seatInstance);
    }

    public List<SeatInstanceDto> getAllSeatInstances() {
        return seatInstanceRepository.findAll().stream().map(SeatInstanceMapper::toDto).toList();
    }

    @Transactional
    public SeatInstanceDto holdSeat(Long id) {
        SeatInstance seatInstance = seatInstanceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("SeatInstance not found with id: " + id));

        if (seatInstance.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatNotAvailableException(
                    "Seat " + id + " is not available (status: " + seatInstance.getStatus() + ")");
        }

        seatInstance.setStatus(SeatStatus.HELD);
        SeatInstance saved = seatInstanceRepository.save(seatInstance);
        return SeatInstanceMapper.toDto(saved);
    }

    /**
     * Compensating action for a multi-seat hold that failed partway through (e.g. booking-service
     * held seats 1 and 2, then seat 3 was already taken) - undoes a HELD seat back to AVAILABLE.
     * Idempotent for AVAILABLE (safe to retry), refuses to touch a BOOKED seat since that would
     * incorrectly free up a seat that's already part of a confirmed booking.
     */
    @Transactional
    public SeatInstanceDto releaseSeat(Long id) {
        SeatInstance seatInstance = seatInstanceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("SeatInstance not found with id: " + id));

        if (seatInstance.getStatus() == SeatStatus.BOOKED) {
            throw new SeatAlreadyBookedException("Seat " + id + " is already booked and cannot be released");
        }

        if (seatInstance.getStatus() == SeatStatus.AVAILABLE) {
            return SeatInstanceMapper.toDto(seatInstance);
        }

        seatInstance.setStatus(SeatStatus.AVAILABLE);
        SeatInstance saved = seatInstanceRepository.save(seatInstance);
        return SeatInstanceMapper.toDto(saved);
    }
}
