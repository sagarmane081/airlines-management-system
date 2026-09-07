package com.services.service;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.services.client.AircraftClient;
import com.services.dto.CabinClassDto;
import com.services.dto.SeatMapDto;
import com.services.entity.CabinClass;
import com.services.entity.CabinClassType;
import com.services.entity.SeatMap;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.CabinClassRepository;
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
class CabinClassServiceTest {

    @Mock
    private CabinClassRepository cabinClassRepository;

    @Mock
    private SeatMapRepository seatMapRepository;

    @Mock
    private AircraftClient aircraftClient;

    @InjectMocks
    private CabinClassService cabinClassService;

    private SeatMap seatMap() {
        return new SeatMap(1L, 100L, 30);
    }

    private SeatMapDto seatMapRef(Long id) {
        SeatMapDto dto = new SeatMapDto();
        dto.setId(id);
        return dto;
    }

    private AircraftDto aircraftOwnedBy(Long ownerId) {
        return new AircraftDto(100L, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE",
                new AirlineDto(10L, "Air India", "AI", null, ownerId));
    }

    @Test
    void createCabinClassSavesAndReturnsDtoForOwningAirlineOwner() {
        CabinClassDto request = new CabinClassDto(null, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMapRef(1L));
        when(seatMapRepository.findById(1L)).thenReturn(Optional.of(seatMap()));
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));
        CabinClass saved = new CabinClass(1L, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMap());
        when(cabinClassRepository.save(any(CabinClass.class))).thenReturn(saved);

        CabinClassDto result = cabinClassService.createCabinClass(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(CabinClassType.ECONOMY, result.getName());
    }

    @Test
    void createCabinClassThrowsForbiddenForCustomer() {
        CabinClassDto request = new CabinClassDto(null, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMapRef(1L));

        assertThrows(ForbiddenException.class, () -> cabinClassService.createCabinClass(request, 1L, "ROLE_CUSTOMER"));
        verify(cabinClassRepository, never()).save(any());
        verifyNoInteractions(seatMapRepository, aircraftClient);
    }

    @Test
    void createCabinClassThrowsForbiddenForNonOwningAirlineOwner() {
        CabinClassDto request = new CabinClassDto(null, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMapRef(1L));
        when(seatMapRepository.findById(1L)).thenReturn(Optional.of(seatMap()));
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> cabinClassService.createCabinClass(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(cabinClassRepository, never()).save(any());
    }

    @Test
    void createCabinClassSucceedsForSystemAdminEvenWhenNotOwner() {
        CabinClassDto request = new CabinClassDto(null, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMapRef(1L));
        when(seatMapRepository.findById(1L)).thenReturn(Optional.of(seatMap()));
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));
        CabinClass saved = new CabinClass(1L, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMap());
        when(cabinClassRepository.save(any(CabinClass.class))).thenReturn(saved);

        CabinClassDto result = cabinClassService.createCabinClass(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void createCabinClassThrowsWhenSeatMapMissing() {
        CabinClassDto request = new CabinClassDto(null, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMapRef(99L));
        when(seatMapRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cabinClassService.createCabinClass(request, 42L, "ROLE_AIRLINE_OWNER"));
        verify(cabinClassRepository, never()).save(any());
    }

    @Test
    void getCabinClassByIdThrowsWhenMissing() {
        when(cabinClassRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cabinClassService.getCabinClassById(99L));
    }

    @Test
    void getAllCabinClassesMapsEveryRow() {
        when(cabinClassRepository.findAll()).thenReturn(List.of(
                new CabinClass(1L, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMap()),
                new CabinClass(2L, CabinClassType.BUSINESS, 1, 4, 4, 40, seatMap())));

        List<CabinClassDto> result = cabinClassService.getAllCabinClasses();

        assertEquals(2, result.size());
        assertEquals(CabinClassType.BUSINESS, result.get(1).getName());
    }
}
