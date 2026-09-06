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
        return flightRepository.findAll().stream().map(this::enrichFlight).toList();
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
        return flightInstanceRepository.findAll().stream()
                .map(instance -> FlightMapper.toDto(instance, enrichFlight(instance.getFlight())))
                .toList();
    }
}