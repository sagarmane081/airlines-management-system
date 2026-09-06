package com.services.service;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.services.entity.Aircraft;
import com.services.entity.AircraftStatus;
import com.services.entity.Airline;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.AircraftRepository;
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
class AircraftServiceTest {

    @Mock
    private AircraftRepository aircraftRepository;

    @Mock
    private AirlineRepository airlineRepository;

    @Mock
    private AirlineService airlineService;

    @InjectMocks
    private AircraftService aircraftService;

    private Airline airline() {
        return new Airline(10L, "Air India", "AI", 100L, 42L);
    }

    @Test
    void createAircraftSavesAndReturnsDtoForOwningAirlineOwner() {
        AircraftDto request = new AircraftDto(null, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE",
                new AirlineDto(10L, null, null, null, null));
        when(airlineRepository.findById(10L)).thenReturn(Optional.of(airline()));
        Aircraft saved = new Aircraft(1L, "VT-ABC", "737-800", "Boeing", 189, AircraftStatus.ACTIVE, airline());
        when(aircraftRepository.save(any(Aircraft.class))).thenReturn(saved);
        when(airlineService.getAirlineById(10L)).thenReturn(new AirlineDto(10L, "Air India", "AI", null, 42L));

        AircraftDto result = aircraftService.createAircraft(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals("VT-ABC", result.getRegistrationNumber());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("Air India", result.getAirline().getName());
    }

    @Test
    void createAircraftThrowsForbiddenForCustomer() {
        AircraftDto request = new AircraftDto(null, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE",
                new AirlineDto(10L, null, null, null, null));

        assertThrows(ForbiddenException.class, () -> aircraftService.createAircraft(request, 1L, "ROLE_CUSTOMER"));
        verify(aircraftRepository, never()).save(any());
        verifyNoInteractions(airlineRepository, airlineService);
    }

    @Test
    void createAircraftThrowsForbiddenForNonOwningAirlineOwner() {
        AircraftDto request = new AircraftDto(null, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE",
                new AirlineDto(10L, null, null, null, null));
        when(airlineRepository.findById(10L)).thenReturn(Optional.of(airline()));

        assertThrows(ForbiddenException.class, () -> aircraftService.createAircraft(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(aircraftRepository, never()).save(any());
    }

    @Test
    void createAircraftSucceedsForSystemAdminEvenWhenNotOwner() {
        AircraftDto request = new AircraftDto(null, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE",
                new AirlineDto(10L, null, null, null, null));
        when(airlineRepository.findById(10L)).thenReturn(Optional.of(airline()));
        Aircraft saved = new Aircraft(1L, "VT-ABC", "737-800", "Boeing", 189, AircraftStatus.ACTIVE, airline());
        when(aircraftRepository.save(any(Aircraft.class))).thenReturn(saved);
        when(airlineService.getAirlineById(10L)).thenReturn(new AirlineDto(10L, "Air India", "AI", null, 42L));

        AircraftDto result = aircraftService.createAircraft(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void createAircraftThrowsWhenAirlineMissing() {
        AircraftDto request = new AircraftDto(null, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE",
                new AirlineDto(999L, null, null, null, null));
        when(airlineRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> aircraftService.createAircraft(request, 42L, "ROLE_SYSTEM_ADMIN"));
        verify(aircraftRepository, never()).save(any());
    }

    @Test
    void getAircraftByIdThrowsWhenMissing() {
        when(aircraftRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> aircraftService.getAircraftById(99L));
    }

    @Test
    void getAllAircraftMakesExactlyOneBulkCallForAirlines() {
        when(aircraftRepository.findAll()).thenReturn(List.of(
                new Aircraft(1L, "VT-ABC", "737-800", "Boeing", 189, AircraftStatus.ACTIVE, airline()),
                new Aircraft(2L, "VT-XYZ", "A320", "Airbus", 180, AircraftStatus.MAINTENANCE, airline())));
        when(airlineService.getAirlinesByIds(anyList())).thenReturn(List.of(new AirlineDto(10L, "Air India", "AI", null, 42L)));

        List<AircraftDto> result = aircraftService.getAllAircraft();

        assertEquals(2, result.size());
        assertEquals("MAINTENANCE", result.get(1).getStatus());
        verify(airlineService, times(1)).getAirlinesByIds(anyList());
    }
}
