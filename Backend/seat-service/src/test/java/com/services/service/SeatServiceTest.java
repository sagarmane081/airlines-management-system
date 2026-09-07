package com.services.service;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.services.client.AircraftClient;
import com.services.dto.CabinClassDto;
import com.services.dto.SeatDto;
import com.services.entity.CabinClass;
import com.services.entity.CabinClassType;
import com.services.entity.Seat;
import com.services.entity.SeatMap;
import com.services.entity.SeatType;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.CabinClassRepository;
import com.services.repository.SeatRepository;
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
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private CabinClassRepository cabinClassRepository;

    @Mock
    private AircraftClient aircraftClient;

    @InjectMocks
    private SeatService seatService;

    private CabinClass cabinClass() {
        return new CabinClass(1L, CabinClassType.ECONOMY, 5, 30, 6, 31, new SeatMap(1L, 100L, 30));
    }

    private CabinClassDto cabinClassRef(Long id) {
        CabinClassDto dto = new CabinClassDto();
        dto.setId(id);
        return dto;
    }

    private AircraftDto aircraftOwnedBy(Long ownerId) {
        return new AircraftDto(100L, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE",
                new AirlineDto(10L, "Air India", "AI", null, ownerId));
    }

    @Test
    void createSeatSavesAndReturnsDtoForOwningAirlineOwner() {
        SeatDto request = new SeatDto(null, 12, "A", SeatType.WINDOW, false, null, cabinClassRef(1L));
        when(cabinClassRepository.findById(1L)).thenReturn(Optional.of(cabinClass()));
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));
        Seat saved = new Seat(1L, 12, "A", SeatType.WINDOW, false, cabinClass());
        when(seatRepository.save(any(Seat.class))).thenReturn(saved);

        SeatDto result = seatService.createSeat(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals("12A", result.getSeatNumber());
        assertEquals(SeatType.WINDOW, result.getSeatType());
    }

    @Test
    void createSeatThrowsForbiddenForCustomer() {
        SeatDto request = new SeatDto(null, 12, "A", SeatType.WINDOW, false, null, cabinClassRef(1L));

        assertThrows(ForbiddenException.class, () -> seatService.createSeat(request, 1L, "ROLE_CUSTOMER"));
        verify(seatRepository, never()).save(any());
        verifyNoInteractions(cabinClassRepository, aircraftClient);
    }

    @Test
    void createSeatThrowsForbiddenForNonOwningAirlineOwner() {
        SeatDto request = new SeatDto(null, 12, "A", SeatType.WINDOW, false, null, cabinClassRef(1L));
        when(cabinClassRepository.findById(1L)).thenReturn(Optional.of(cabinClass()));
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> seatService.createSeat(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(seatRepository, never()).save(any());
    }

    @Test
    void createSeatSucceedsForSystemAdminEvenWhenNotOwner() {
        SeatDto request = new SeatDto(null, 12, "A", SeatType.WINDOW, false, null, cabinClassRef(1L));
        when(cabinClassRepository.findById(1L)).thenReturn(Optional.of(cabinClass()));
        when(aircraftClient.getAircraftById(100L)).thenReturn(aircraftOwnedBy(42L));
        Seat saved = new Seat(1L, 12, "A", SeatType.WINDOW, false, cabinClass());
        when(seatRepository.save(any(Seat.class))).thenReturn(saved);

        SeatDto result = seatService.createSeat(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void createSeatThrowsWhenCabinClassMissing() {
        SeatDto request = new SeatDto(null, 12, "A", SeatType.WINDOW, false, null, cabinClassRef(99L));
        when(cabinClassRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> seatService.createSeat(request, 42L, "ROLE_AIRLINE_OWNER"));
        verify(seatRepository, never()).save(any());
    }

    @Test
    void getSeatByIdThrowsWhenMissing() {
        when(seatRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> seatService.getSeatById(99L));
    }

    @Test
    void getAllSeatsMapsEveryRow() {
        when(seatRepository.findAll()).thenReturn(List.of(
                new Seat(1L, 12, "A", SeatType.WINDOW, false, cabinClass()),
                new Seat(2L, 12, "B", SeatType.MIDDLE, false, cabinClass())));

        List<SeatDto> result = seatService.getAllSeats();

        assertEquals(2, result.size());
        assertEquals("12B", result.get(1).getSeatNumber());
    }
}
