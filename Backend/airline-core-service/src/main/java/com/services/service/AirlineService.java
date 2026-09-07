package com.services.service;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import com.services.client.LocationClient;
import com.services.entity.Airline;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.AirlineMapper;
import com.services.repository.AirlineRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AirlineService {

    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final AirlineRepository airlineRepository;
    private final LocationClient locationClient;

    public AirlineService(AirlineRepository airlineRepository, LocationClient locationClient) {
        this.airlineRepository = airlineRepository;
        this.locationClient = locationClient;
    }

    public AirlineDto createAirline(AirlineDto airlineDto, String requesterRole) {
        if (!ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_SYSTEM_ADMIN + " can create airlines");
        }
        Airline saved = airlineRepository.save(AirlineMapper.toEntity(airlineDto));
        CityDto city = locationClient.getCityById(saved.getHeadquartersCityId());
        return AirlineMapper.toDto(saved, city);
    }

    // Highest-value cache target in the whole codebase: this is the ownership-check lookup called
    // on every create* request across 6 services. No update/delete endpoint exists for Airline yet,
    // so no @CacheEvict is needed - if one is ever added, it MUST evict("airlines", #id).
    @Cacheable(value = "airlines", key = "#id")
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