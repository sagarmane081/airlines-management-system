package com.services.service;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import com.services.client.AirlineClient;
import com.services.client.LocationClient;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.FlightMapper;
import com.services.repository.FlightInstanceRepository;
import com.services.repository.FlightRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FlightService {

    private final FlightRepository flightRepository;
    private final FlightInstanceRepository flightInstanceRepository;
    private final AirlineClient airlineClient;
    private final LocationClient locationClient;

    public FlightService(FlightRepository flightRepository,
                         FlightInstanceRepository flightInstanceRepository,
                         AirlineClient airlineClient,
                         LocationClient locationClient) {
        this.flightRepository = flightRepository;
        this.flightInstanceRepository = flightInstanceRepository;
        this.airlineClient = airlineClient;
        this.locationClient = locationClient;
    }

    private FlightDto enrichFlight(Flight flight) {
        AirlineDto airline = airlineClient.getAirlineById(flight.getAirlineId());
        CityDto departureCity = locationClient.getCityById(flight.getDepartureCityId());
        CityDto arrivalCity = locationClient.getCityById(flight.getArrivalCityId());
        return FlightMapper.toDto(flight, airline, departureCity, arrivalCity);
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
        List<Long> cityIds = Stream.concat(
                        flights.stream().map(Flight::getDepartureCityId),
                        flights.stream().map(Flight::getArrivalCityId))
                .distinct()
                .toList();

        Map<Long, AirlineDto> airlinesById = airlineClient.getAirlinesByIds(airlineIds).stream()
                .collect(Collectors.toMap(AirlineDto::getId, Function.identity()));
        Map<Long, CityDto> citiesById = locationClient.getCitiesByIds(cityIds).stream()
                .collect(Collectors.toMap(CityDto::getId, Function.identity()));

        return flights.stream()
                .map(f -> FlightMapper.toDto(f,
                        airlinesById.get(f.getAirlineId()),
                        citiesById.get(f.getDepartureCityId()),
                        citiesById.get(f.getArrivalCityId())))
                .toList();
    }

    public FlightDto createFlight(FlightDto flightDto) {
        Flight saved = flightRepository.save(FlightMapper.toEntity(flightDto));
        return enrichFlight(saved);
    }

    public FlightDto getFlightById(Long id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + id));
        return enrichFlight(flight);
    }

    public List<FlightDto> getAllFlights() {
        return enrichFlights(flightRepository.findAll());
    }

    public FlightInstanceDto createFlightInstance(FlightInstanceDto instanceDto) {
        Long flightId = instanceDto.getFlight().getId();
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + flightId));

        FlightInstance instance = new FlightInstance();
        instance.setFlight(flight);
        instance.setDepartureTime(instanceDto.getDepartureTime());
        instance.setArrivalTime(instanceDto.getArrivalTime());
        instance.setStatus(instanceDto.getStatus());

        FlightInstance saved = flightInstanceRepository.save(instance);
        return FlightMapper.toDto(saved, enrichFlight(saved.getFlight()));
    }

    public FlightInstanceDto getFlightInstanceById(Long id) {
        FlightInstance instance = flightInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightInstance not found with id: " + id));
        return FlightMapper.toDto(instance, enrichFlight(instance.getFlight()));
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

        return instances.stream()
                .map(instance -> FlightMapper.toDto(instance, flightDtoById.get(instance.getFlight().getId())))
                .toList();
    }
}