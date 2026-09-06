package com.services.service;

import com.common.dto.CityDto;
import com.services.entity.City;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.CityMapper;
import com.services.repository.CityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CityService {

    private final CityRepository cityRepository;

    public CityService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    public CityDto createCity(CityDto cityDto) {
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
}