package com.services.service;

import com.common.dto.AirportDto;
import com.common.dto.CityDto;
import com.services.entity.Airport;
import com.services.entity.City;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.AirportRepository;
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
class AirportServiceTest {

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private CityRepository cityRepository;

    @InjectMocks
    private AirportService airportService;

    private City mumbai() {
        return new City(100L, "Mumbai", "India", "Asia/Kolkata");
    }

    @Test
    void createAirportSavesAndReturnsDtoForSystemAdmin() {
        AirportDto request = new AirportDto(null, "BOM", "Chhatrapati Shivaji", new CityDto(100L, null, null, null));
        when(cityRepository.findById(100L)).thenReturn(Optional.of(mumbai()));
        Airport saved = new Airport(1L, "BOM", "Chhatrapati Shivaji", mumbai());
        when(airportRepository.save(any(Airport.class))).thenReturn(saved);

        AirportDto result = airportService.createAirport(request, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
        assertEquals("BOM", result.getIataCode());
        assertEquals("Mumbai", result.getCity().getName());
    }

    @Test
    void createAirportThrowsForbiddenForNonAdmin() {
        AirportDto request = new AirportDto(null, "BOM", "Chhatrapati Shivaji", new CityDto(100L, null, null, null));

        assertThrows(ForbiddenException.class, () -> airportService.createAirport(request, "ROLE_CUSTOMER"));
        verify(airportRepository, never()).save(any());
        verifyNoInteractions(cityRepository);
    }

    @Test
    void createAirportThrowsWhenCityMissing() {
        AirportDto request = new AirportDto(null, "BOM", "Chhatrapati Shivaji", new CityDto(999L, null, null, null));
        when(cityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> airportService.createAirport(request, "ROLE_SYSTEM_ADMIN"));
        verify(airportRepository, never()).save(any());
    }

    @Test
    void getAirportByIdThrowsWhenMissing() {
        when(airportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> airportService.getAirportById(99L));
    }

    @Test
    void getAllAirportsMapsEveryRow() {
        when(airportRepository.findAll()).thenReturn(List.of(
                new Airport(1L, "BOM", "Chhatrapati Shivaji", mumbai()),
                new Airport(2L, "DEL", "Indira Gandhi", mumbai())));

        List<AirportDto> result = airportService.getAllAirports();

        assertEquals(2, result.size());
        assertEquals("DEL", result.get(1).getIataCode());
    }
}
