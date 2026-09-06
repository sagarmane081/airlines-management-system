package com.services.service;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import com.services.client.LocationClient;
import com.services.entity.Airline;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.AirlineMapper;
import com.services.repository.AirlineRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AirlineService {

    private final AirlineRepository airlineRepository;
    private final LocationClient locationClient;

    public AirlineService(AirlineRepository airlineRepository, LocationClient locationClient) {
        this.airlineRepository = airlineRepository;
        this.locationClient = locationClient;
    }

    public AirlineDto createAirline(AirlineDto airlineDto) {
        Airline saved = airlineRepository.save(AirlineMapper.toEntity(airlineDto));
        CityDto city = locationClient.getCityById(saved.getHeadquartersCityId());
        return AirlineMapper.toDto(saved, city);
    }

    public AirlineDto getAirlineById(Long id) {
        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airline not found with id: " + id));
        CityDto city = locationClient.getCityById(airline.getHeadquartersCityId());
        return AirlineMapper.toDto(airline, city);
    }

    public List<AirlineDto> getAllAirlines() {
        return enrichAirlines(airlineRepository.findAll());
    }

    public List<AirlineDto> getAirlinesByIds(List<Long> ids) {
        return enrichAirlines(airlineRepository.findAllByIdIn(ids));
    }

    private List<AirlineDto> enrichAirlines(List<Airline> airlines) {
        if (airlines.isEmpty()) {
            return List.of();
        }

        List<Long> cityIds = airlines.stream()
                .map(Airline::getHeadquartersCityId)
                .distinct()
                .toList();
        Map<Long, CityDto> citiesById = locationClient.getCitiesByIds(cityIds).stream()
                .collect(Collectors.toMap(CityDto::getId, Function.identity()));

        return airlines.stream()
                .map(a -> AirlineMapper.toDto(a, citiesById.get(a.getHeadquartersCityId())))
                .toList();
    }
}