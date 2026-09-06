package com.services.service;

import com.common.dto.AirportDto;
import com.services.entity.Airport;
import com.services.entity.City;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.AirportMapper;
import com.services.repository.AirportRepository;
import com.services.repository.CityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AirportService {

    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final AirportRepository airportRepository;
    private final CityRepository cityRepository;

    public AirportService(AirportRepository airportRepository, CityRepository cityRepository) {
        this.airportRepository = airportRepository;
        this.cityRepository = cityRepository;
    }

    public AirportDto createAirport(AirportDto airportDto, String requesterRole) {
        if (!ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_SYSTEM_ADMIN + " can create airports");
        }
        Long cityId = airportDto.getCity() != null ? airportDto.getCity().getId() : null;
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id: " + cityId));

        Airport saved = airportRepository.save(AirportMapper.toEntity(airportDto, city));
        return AirportMapper.toDto(saved);
    }

    public AirportDto getAirportById(Long id) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found with id: " + id));
        return AirportMapper.toDto(airport);
    }

    public List<AirportDto> getAllAirports() {
        return airportRepository.findAll().stream().map(AirportMapper::toDto).toList();
    }

    public List<AirportDto> getAirportsByIds(List<Long> ids) {
        return airportRepository.findAllByIdIn(ids).stream().map(AirportMapper::toDto).toList();
    }
}
