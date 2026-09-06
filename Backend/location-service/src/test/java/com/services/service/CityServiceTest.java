package com.services.service;

import com.common.dto.CityDto;
import com.services.entity.City;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.CityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

    @Mock
    private CityRepository cityRepository;

    @InjectMocks
    private CityService cityService;

    @Test
    void createCitySavesAndReturnsDto() {
        CityDto request = new CityDto(null, "Mumbai", "India", "Asia/Kolkata");
        City saved = new City(1L, "Mumbai", "India", "Asia/Kolkata");
        when(cityRepository.save(any(City.class))).thenReturn(saved);

        CityDto result = cityService.createCity(request);

        assertEquals(1L, result.getId());
        assertEquals("Mumbai", result.getName());
    }

    @Test
    void getCityByIdReturnsDtoWhenFound() {
        City city = new City(1L, "Mumbai", "India", "Asia/Kolkata");
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));

        CityDto result = cityService.getCityById(1L);

        assertEquals("Mumbai", result.getName());
    }

    @Test
    void getCityByIdThrowsWhenMissing() {
        when(cityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cityService.getCityById(99L));
    }

    @Test
    void getAllCitiesMapsEveryRow() {
        when(cityRepository.findAll()).thenReturn(List.of(
                new City(1L, "Mumbai", "India", "Asia/Kolkata"),
                new City(2L, "Delhi", "India", "Asia/Kolkata")));

        List<CityDto> result = cityService.getAllCities();

        assertEquals(2, result.size());
        assertEquals("Delhi", result.get(1).getName());
    }
}
