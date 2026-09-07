package com.services.service;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.services.client.AircraftClient;
import com.services.dto.SeatMapDto;
import com.services.entity.SeatMap;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.SeatMapRepository;
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
class SeatMapServiceTest {

    @Mock
    private SeatMapRepository seatMapRepository;

    @Mock
    private AircraftClient aircraftClient;

    @InjectMocks
    private SeatMapService seatMapService;

    private AircraftDto aircraftOwnedBy(Long ownerId) {
        return new AircraftDto(100L, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE",
                new AirlineDto(10L, "Air India", "AI", null, ownerId));
    }

    @Test
    void createSeatMapSavesAndReturnsDtoForOwningAirlineOwner() {
        SeatMapDto request = new SeatMapDto(null, 100L, 30);
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));
        when(seatMapRepository.save(any(SeatMap.class))).thenReturn(new SeatMap(1L, 100L, 30));

        SeatMapDto result = seatMapService.createSeatMap(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(30, result.getTotalRows());
    }

    @Test
    void createSeatMapThrowsForbiddenForCustomer() {
        SeatMapDto request = new SeatMapDto(null, 100L, 30);

        assertThrows(ForbiddenException.class, () -> seatMapService.createSeatMap(request, 1L, "ROLE_CUSTOMER"));
        verify(seatMapRepository, never()).save(any());
        verifyNoInteractions(aircraftClient);
    }

    @Test
    void createSeatMapThrowsForbiddenForNonOwningAirlineOwner() {
        SeatMapDto request = new SeatMapDto(null, 100L, 30);
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> seatMapService.createSeatMap(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(seatMapRepository, never()).save(any());
    }

    @Test
    void createSeatMapSucceedsForSystemAdminEvenWhenNotOwner() {
        SeatMapDto request = new SeatMapDto(null, 100L, 30);
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));
        when(seatMapRepository.save(any(SeatMap.class))).thenReturn(new SeatMap(1L, 100L, 30));

        SeatMapDto result = seatMapService.createSeatMap(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void getSeatMapByIdThrowsWhenMissing() {
        when(seatMapRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> seatMapService.getSeatMapById(99L));
    }

    @Test
    void getAllSeatMapsMapsEveryRow() {
        when(seatMapRepository.findAll()).thenReturn(List.of(
                new SeatMap(1L, 100L, 30),
                new SeatMap(2L, 200L, 20)));

        List<SeatMapDto> result = seatMapService.getAllSeatMaps();

        assertEquals(2, result.size());
        assertEquals(20, result.get(1).getTotalRows());
    }
}
