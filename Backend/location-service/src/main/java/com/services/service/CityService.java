package com.services.service;

import com.common.dto.CityDto;
import com.services.entity.City;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.CityMapper;
import com.services.repository.CityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CityService {

    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final CityRepository cityRepository;

    public CityService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    public CityDto createCity(CityDto cityDto, String requesterRole) {
        if (!ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_SYSTEM_ADMIN + " can create cities");
        }
        City city = CityMapper.toEntity(cityDto);
        City saved = cityRepository.save(city);
        return CityMapper.toDto(saved);
    }

    public CityDto getCityById(Long id) {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id: " + id));
        return CityMapper.toDto(city);
    }

    public List<CityDto> getAllCities() {
        return cityRepository.findAll()
                .stream()
                .map(CityMapper::toDto)
                .toList();
    }

    public List<CityDto> getCitiesByIds(List<Long> ids) {
        return cityRepository.findAllByIdIn(ids)
                .stream()
                .map(CityMapper::toDto)
                .toList();
    }
}