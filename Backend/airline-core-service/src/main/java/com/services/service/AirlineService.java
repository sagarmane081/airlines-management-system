package com.services.service;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import com.services.client.LocationClient;
import com.services.entity.Airline;
import com.services.mapper.AirlineMapper;
import com.services.repository.AirlineRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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
                .orElseThrow(() -> new RuntimeException("Airline not found with id: " + id));
        CityDto city = locationClient.getCityById(airline.getHeadquartersCityId());
        return AirlineMapper.toDto(airline, city);
    }

    public List<AirlineDto> getAllAirlines() {
        return airlineRepository.findAll()
                .stream()
                .map(a -> AirlineMapper.toDto(a, locationClient.getCityById(a.getHeadquartersCityId())))
                .toList();
    }
}