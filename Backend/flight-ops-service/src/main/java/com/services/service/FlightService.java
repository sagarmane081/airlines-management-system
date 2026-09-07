package com.services.service;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.common.dto.AirportDto;
import com.services.client.AircraftClient;
import com.services.client.AirlineClient;
import com.services.client.LocationClient;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.FlightMapper;
import com.services.repository.FlightInstanceRepository;
import com.services.repository.FlightRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FlightService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final FlightRepository flightRepository;
    private final FlightInstanceRepository flightInstanceRepository;
    private final AirlineClient airlineClient;
    private final LocationClient locationClient;
    private final AircraftClient aircraftClient;

    public FlightService(FlightRepository flightRepository,
                         FlightInstanceRepository flightInstanceRepository,
                         AirlineClient airlineClient,
                         LocationClient locationClient,
                         AircraftClient aircraftClient) {
        this.flightRepository = flightRepository;
        this.flightInstanceRepository = flightInstanceRepository;
        this.airlineClient = airlineClient;
        this.locationClient = locationClient;
        this.aircraftClient = aircraftClient;
    }

    // Package-private, not private - FlightScheduleService reuses this rather than duplicating
    // the same three Feign calls, same reasoning as AircraftService calling AirlineService
    // directly in airline-core-service.
    FlightDto enrichFlight(Flight flight) {
        AirlineDto airline = airlineClient.getAirlineById(flight.getAirlineId());
        AirportDto departureAirport = locationClient.getAirportById(flight.getDepartureAirportId());
        AirportDto arrivalAirport = locationClient.getAirportById(flight.getArrivalAirportId());
        return FlightMapper.toDto(flight, airline, departureAirport, arrivalAirport);
    }

    /**
     * Enriches a whole batch of flights with exactly one Feign call to airline-core-service and
     * one to location-service, regardless of how many flights are in the list - avoids the N+1
     * (previously up to 3 calls per row) that {@link #enrichFlight} has for a single flight.
     */
    private List<FlightDto> enrichFlights(List<Flight> flights) {
        if (flights.isEmpty()) {
            return List.of();
        }

        List<Long> airlineIds = flights.stream().map(Flight::getAirlineId).distinct().toList();
        List<Long> airportIds = Stream.concat(
                        flights.stream().map(Flight::getDepartureAirportId),
                        flights.stream().map(Flight::getArrivalAirportId))
                .distinct()
                .toList();

        Map<Long, AirlineDto> airlinesById = airlineClient.getAirlinesByIds(airlineIds).stream()
                .collect(Collectors.toMap(AirlineDto::getId, Function.identity()));
        Map<Long, AirportDto> airportsById = locationClient.getAirportsByIds(airportIds).stream()
                .collect(Collectors.toMap(AirportDto::getId, Function.identity()));

        return flights.stream()
                .map(f -> FlightMapper.toDto(f,
                        airlinesById.get(f.getAirlineId()),
                        airportsById.get(f.getDepartureAirportId()),
                        airportsById.get(f.getArrivalAirportId())))
                .toList();
    }

    private AircraftDto enrichAircraft(Long aircraftId) {
        return aircraftId != null ? aircraftClient.getAircraftById(aircraftId) : null;
    }

    private void requireAirlineManager(String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can manage flights");
        }
    }

    public FlightDto createFlight(FlightDto flightDto, Long requesterId, String requesterRole) {
        requireAirlineManager(requesterRole);
        Long airlineId = flightDto.getAirline().getId();
        AirlineDto airline = airlineClient.getAirlineById(airlineId);
        AirlineOwnershipChecker.requireAirlineOwnership(airline, requesterId, requesterRole);

        Flight saved = flightRepository.save(FlightMapper.toEntity(flightDto));
        AirportDto departureAirport = locationClient.getAirportById(saved.getDepartureAirportId());
        AirportDto arrivalAirport = locationClient.getAirportById(saved.getArrivalAirportId());
        return FlightMapper.toDto(saved, airline, departureAirport, arrivalAirport);
    }

    public FlightDto getFlightById(Long id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + id));
        return enrichFlight(flight);
    }

    public List<FlightDto> getAllFlights() {
        return enrichFlights(flightRepository.findAll());
    }

    public FlightInstanceDto createFlightInstance(FlightInstanceDto instanceDto, Long requesterId, String requesterRole) {
        requireAirlineManager(requesterRole);
        Long flightId = instanceDto.getFlight().getId();
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + flightId));

        AirlineDto airline = airlineClient.getAirlineById(flight.getAirlineId());
        AirlineOwnershipChecker.requireAirlineOwnership(airline, requesterId, requesterRole);

        FlightInstance instance = new FlightInstance();
        instance.setFlight(flight);
        instance.setDepartureTime(instanceDto.getDepartureTime());
        instance.setArrivalTime(instanceDto.getArrivalTime());
        instance.setStatus(instanceDto.getStatus());
        instance.setAircraftId(instanceDto.getAircraft() != null ? instanceDto.getAircraft().getId() : null);

        FlightInstance saved = flightInstanceRepository.save(instance);
        AirportDto departureAirport = locationClient.getAirportById(flight.getDepartureAirportId());
        AirportDto arrivalAirport = locationClient.getAirportById(flight.getArrivalAirportId());
        FlightDto flightDto = FlightMapper.toDto(flight, airline, departureAirport, arrivalAirport);
        return FlightMapper.toDto(saved, flightDto, enrichAircraft(saved.getAircraftId()));
    }

    public FlightInstanceDto getFlightInstanceById(Long id) {
        FlightInstance instance = flightInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightInstance not found with id: " + id));
        return FlightMapper.toDto(instance, enrichFlight(instance.getFlight()), enrichAircraft(instance.getAircraftId()));
    }

    public List<FlightInstanceDto> getAllFlightInstances() {
        List<FlightInstance> instances = flightInstanceRepository.findAll();

        List<Flight> distinctFlights = instances.stream()
                .collect(Collectors.toMap(i -> i.getFlight().getId(), FlightInstance::getFlight, (a, b) -> a))
                .values()
                .stream()
                .toList();
        Map<Long, FlightDto> flightDtoById = enrichFlights(distinctFlights).stream()
                .collect(Collectors.toMap(FlightDto::getId, Function.identity()));

        List<Long> aircraftIds = instances.stream()
                .map(FlightInstance::getAircraftId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, AircraftDto> aircraftById = aircraftIds.isEmpty()
                ? new HashMap<>()
                : aircraftClient.getAircraftByIds(aircraftIds).stream()
                        .collect(Collectors.toMap(AircraftDto::getId, Function.identity()));

        return instances.stream()
                .map(instance -> FlightMapper.toDto(instance, flightDtoById.get(instance.getFlight().getId()),
                        aircraftById.get(instance.getAircraftId())))
                .toList();
    }
}
