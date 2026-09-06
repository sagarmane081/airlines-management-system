package com.services.service;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import com.services.client.AirlineClient;
import com.services.client.LocationClient;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;
import com.services.entity.FlightInstanceStatus;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.FlightInstanceRepository;
import com.services.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightInstanceRepository flightInstanceRepository;

    @Mock
    private AirlineClient airlineClient;

    @Mock
    private LocationClient locationClient;

    @InjectMocks
    private FlightService flightService;

    @Test
    void getFlightByIdThrowsWhenMissing() {
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightService.getFlightById(99L));
        verifyNoInteractions(airlineClient, locationClient);
    }

    @Test
    void getAllFlightsMakesExactlyOneBulkCallPerDependency() {
        Flight flight1 = new Flight(1L, 10L, "AI101", 100L, 100L);
        Flight flight2 = new Flight(2L, 20L, "6E202", 200L, 300L);
        when(flightRepository.findAll()).thenReturn(List.of(flight1, flight2));

        when(airlineClient.getAirlinesByIds(anyList())).thenReturn(List.of(
                new AirlineDto(10L, "Air India", "AI", null),
                new AirlineDto(20L, "IndiGo", "6E", null)));
        when(locationClient.getCitiesByIds(anyList())).thenReturn(List.of(
                new CityDto(100L, "Mumbai", "India", "Asia/Kolkata"),
                new CityDto(200L, "Delhi", "India", "Asia/Kolkata"),
                new CityDto(300L, "Bangalore", "India", "Asia/Kolkata")));

        List<FlightDto> result = flightService.getAllFlights();

        assertEquals(2, result.size());
        assertEquals("Air India", result.get(0).getAirline().getName());
        assertEquals("Bangalore", result.get(1).getArrivalCity().getName());
        verify(airlineClient, times(1)).getAirlinesByIds(anyList());
        verify(locationClient, times(1)).getCitiesByIds(anyList());
    }

    @Test
    void getAllFlightInstancesDedupesSharedFlightBeforeEnriching() {
        Flight flight = new Flight(1L, 10L, "AI101", 100L, 100L);
        FlightInstance instance1 = new FlightInstance(1L, flight, LocalDateTime.now(), LocalDateTime.now(), FlightInstanceStatus.SCHEDULED);
        FlightInstance instance2 = new FlightInstance(2L, flight, LocalDateTime.now(), LocalDateTime.now(), FlightInstanceStatus.SCHEDULED);
        when(flightInstanceRepository.findAll()).thenReturn(List.of(instance1, instance2));

        when(airlineClient.getAirlinesByIds(anyList())).thenReturn(List.of(new AirlineDto(10L, "Air India", "AI", null)));
        when(locationClient.getCitiesByIds(anyList())).thenReturn(List.of(new CityDto(100L, "Mumbai", "India", "Asia/Kolkata")));

        List<FlightInstanceDto> result = flightService.getAllFlightInstances();

        assertEquals(2, result.size());
        assertEquals("Air India", result.get(0).getFlight().getAirline().getName());
        assertEquals("Air India", result.get(1).getFlight().getAirline().getName());
        // Both instances share one flight - enrichment must happen once, not once per instance.
        verify(airlineClient, times(1)).getAirlinesByIds(anyList());
        verify(locationClient, times(1)).getCitiesByIds(anyList());
    }
}
