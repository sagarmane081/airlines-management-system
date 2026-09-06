package com.services.service;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import com.services.client.LocationClient;
import com.services.entity.Airline;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.AirlineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AirlineServiceTest {

    @Mock
    private AirlineRepository airlineRepository;

    @Mock
    private LocationClient locationClient;

    @InjectMocks
    private AirlineService airlineService;

    @Test
    void createAirlineEnrichesWithCity() {
        Airline saved = new Airline(1L, "Air India", "AI", 5L);
        when(airlineRepository.save(any(Airline.class))).thenReturn(saved);
        when(locationClient.getCityById(5L)).thenReturn(new CityDto(5L, "Mumbai", "India", "Asia/Kolkata"));

        AirlineDto request = new AirlineDto(null, "Air India", "AI", new CityDto(5L, null, null, null));
        AirlineDto result = airlineService.createAirline(request);

        assertEquals("Mumbai", result.getHeadquartersCity().getName());
    }

    @Test
    void getAirlineByIdThrowsWhenMissingAndNeverCallsLocationService() {
        when(airlineRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> airlineService.getAirlineById(99L));
        verifyNoInteractions(locationClient);
    }

    @Test
    void getAllAirlinesMakesExactlyOneBulkCityCall() {
        when(airlineRepository.findAll()).thenReturn(List.of(
                new Airline(1L, "Air India", "AI", 5L),
                new Airline(2L, "IndiGo", "6E", 6L)));
        when(locationClient.getCitiesByIds(anyList())).thenReturn(List.of(
                new CityDto(5L, "Mumbai", "India", "Asia/Kolkata"),
                new CityDto(6L, "Delhi", "India", "Asia/Kolkata")));

        List<AirlineDto> result = airlineService.getAllAirlines();

        assertEquals(2, result.size());
        assertEquals("Mumbai", result.get(0).getHeadquartersCity().getName());
        assertEquals("Delhi", result.get(1).getHeadquartersCity().getName());
        verify(locationClient, times(1)).getCitiesByIds(anyList());
        verify(locationClient, never()).getCityById(any());
    }

    @Test
    void getAllAirlinesReturnsEmptyListWithoutCallingLocationService() {
        when(airlineRepository.findAll()).thenReturn(List.of());

        List<AirlineDto> result = airlineService.getAllAirlines();

        assertTrue(result.isEmpty());
        verifyNoInteractions(locationClient);
    }
}
