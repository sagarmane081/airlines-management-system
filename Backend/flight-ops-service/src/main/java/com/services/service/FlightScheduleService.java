package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.AirlineClient;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.dto.FlightScheduleDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;
import com.services.entity.FlightInstanceStatus;
import com.services.entity.FlightSchedule;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.FlightMapper;
import com.services.mapper.FlightScheduleMapper;
import com.services.repository.FlightInstanceRepository;
import com.services.repository.FlightRepository;
import com.services.repository.FlightScheduleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlightScheduleService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final FlightScheduleRepository flightScheduleRepository;
    private final FlightRepository flightRepository;
    private final FlightInstanceRepository flightInstanceRepository;
    private final AirlineClient airlineClient;
    private final FlightService flightService;

    public FlightScheduleService(FlightScheduleRepository flightScheduleRepository, FlightRepository flightRepository,
                                  FlightInstanceRepository flightInstanceRepository, AirlineClient airlineClient,
                                  FlightService flightService) {
        this.flightScheduleRepository = flightScheduleRepository;
        this.flightRepository = flightRepository;
        this.flightInstanceRepository = flightInstanceRepository;
        this.airlineClient = airlineClient;
        this.flightService = flightService;
    }

    private void requireAirlineManager(String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can manage flight schedules");
        }
    }

    private Flight requireOwnedFlight(Long flightId, Long requesterId, String requesterRole) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + flightId));
        AirlineDto airline = airlineClient.getAirlineById(flight.getAirlineId());
        AirlineOwnershipChecker.requireAirlineOwnership(airline, requesterId, requesterRole);
        return flight;
    }

    public FlightScheduleDto createFlightSchedule(FlightScheduleDto scheduleDto, Long requesterId, String requesterRole) {
        requireAirlineManager(requesterRole);
        Long flightId = scheduleDto.getFlight() != null ? scheduleDto.getFlight().getId() : null;
        Flight flight = requireOwnedFlight(flightId, requesterId, requesterRole);

        FlightSchedule saved = flightScheduleRepository.save(FlightScheduleMapper.toEntity(scheduleDto, flight));
        return FlightScheduleMapper.toDto(saved, flightService.enrichFlight(flight));
    }

    public FlightScheduleDto getFlightScheduleById(Long id) {
        FlightSchedule schedule = flightScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightSchedule not found with id: " + id));
        return FlightScheduleMapper.toDto(schedule, flightService.enrichFlight(schedule.getFlight()));
    }

    public List<FlightScheduleDto> getAllFlightSchedules() {
        return flightScheduleRepository.findAll().stream()
                .map(schedule -> FlightScheduleMapper.toDto(schedule, flightService.enrichFlight(schedule.getFlight())))
                .toList();
    }

    /**
     * Materializes one FlightInstance per calendar date between startDate and endDate (inclusive)
     * whose day of week is in operatingDays. Idempotent - re-running for a date that already has a
     * generated instance skips it rather than creating a duplicate, so this is safe to call again
     * after adding more dates to a schedule's range or simply retrying.
     *
     * <p>Known simplification: assumes the flight arrives the same calendar date it departs. A
     * schedule crossing midnight (e.g. departs 23:30, arrives 01:00) would need day-rollover
     * handling this doesn't do.
     */
    public List<FlightInstanceDto> generateInstances(Long scheduleId, Long requesterId, String requesterRole) {
        requireAirlineManager(requesterRole);
        FlightSchedule schedule = flightScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("FlightSchedule not found with id: " + scheduleId));
        AirlineDto airline = airlineClient.getAirlineById(schedule.getFlight().getAirlineId());
        AirlineOwnershipChecker.requireAirlineOwnership(airline, requesterId, requesterRole);

        List<FlightInstance> created = new ArrayList<>();
        for (LocalDate date = schedule.getStartDate(); !date.isAfter(schedule.getEndDate()); date = date.plusDays(1)) {
            if (!schedule.getOperatingDays().contains(date.getDayOfWeek())) {
                continue;
            }

            LocalDateTime departureTime = LocalDateTime.of(date, schedule.getDepartureTime());
            if (flightInstanceRepository.existsByFlightScheduleIdAndDepartureTime(scheduleId, departureTime)) {
                continue;
            }

            FlightInstance instance = new FlightInstance();
            instance.setFlight(schedule.getFlight());
            instance.setFlightSchedule(schedule);
            instance.setDepartureTime(departureTime);
            instance.setArrivalTime(LocalDateTime.of(date, schedule.getArrivalTime()));
            instance.setStatus(FlightInstanceStatus.SCHEDULED);
            created.add(flightInstanceRepository.save(instance));
        }

        FlightDto flightDto = flightService.enrichFlight(schedule.getFlight());
        return created.stream().map(instance -> FlightMapper.toDto(instance, flightDto, null)).toList();
    }
}
