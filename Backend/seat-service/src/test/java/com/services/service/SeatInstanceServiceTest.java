package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.FlightClient;
import com.services.dto.FlightInstanceOwnerView;
import com.services.dto.FlightOwnerView;
import com.services.dto.SeatDto;
import com.services.dto.SeatInstanceDto;
import com.services.entity.CabinClass;
import com.services.entity.CabinClassType;
import com.services.entity.Seat;
import com.services.entity.SeatInstance;
import com.services.entity.SeatMap;
import com.services.entity.SeatStatus;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.exception.SeatAlreadyBookedException;
import com.services.exception.SeatNotAvailableException;
import com.services.repository.SeatInstanceRepository;
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
class SeatInstanceServiceTest {

    @Mock
    private SeatInstanceRepository seatInstanceRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private FlightClient flightClient;

    @InjectMocks
    private SeatInstanceService seatInstanceService;

    private FlightInstanceOwnerView flightInstanceOwnedBy(Long ownerId) {
        return new FlightInstanceOwnerView(1L, new FlightOwnerView(1L, new AirlineDto(10L, "Air India", "AI", null, ownerId)));
    }

    private SeatDto seatRef(Long id) {
        SeatDto dto = new SeatDto();
        dto.setId(id);
        return dto;
    }

    private Seat seat(Long id) {
        SeatMap seatMap = new SeatMap(1L, 100L, 30);
        CabinClass cabinClass = new CabinClass(1L, CabinClassType.ECONOMY, 5, 30, 6, 31, seatMap);
        Seat seat = new Seat();
        seat.setId(id);
        seat.setSeatRow(12);
        seat.setColumnLetter("A");
        seat.setCabinClass(cabinClass);
        return seat;
    }

    @Test
    void createSeatInstanceSavesAndReturnsDtoForOwningAirlineOwner() {
        SeatInstanceDto request = new SeatInstanceDto(null, 1L, seatRef(1L), SeatStatus.AVAILABLE);
        when(flightClient.getFlightInstanceById(1L)).thenReturn(flightInstanceOwnedBy(42L));
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat(1L)));
        SeatInstance saved = new SeatInstance(1L, 1L, seat(1L), SeatStatus.AVAILABLE);
        when(seatInstanceRepository.save(any(SeatInstance.class))).thenReturn(saved);

        SeatInstanceDto result = seatInstanceService.createSeatInstance(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(SeatStatus.AVAILABLE, result.getStatus());
        assertEquals("12A", result.getSeat().getSeatNumber());
    }

    @Test
    void createSeatInstanceThrowsForbiddenForCustomer() {
        SeatInstanceDto request = new SeatInstanceDto(null, 1L, seatRef(1L), SeatStatus.AVAILABLE);

        assertThrows(ForbiddenException.class, () -> seatInstanceService.createSeatInstance(request, 1L, "ROLE_CUSTOMER"));
        verify(seatInstanceRepository, never()).save(any());
        verifyNoInteractions(flightClient);
    }

    @Test
    void createSeatInstanceThrowsForbiddenForNonOwningAirlineOwner() {
        SeatInstanceDto request = new SeatInstanceDto(null, 1L, seatRef(1L), SeatStatus.AVAILABLE);
        when(flightClient.getFlightInstanceById(1L)).thenReturn(flightInstanceOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> seatInstanceService.createSeatInstance(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(seatInstanceRepository, never()).save(any());
    }

    @Test
    void createSeatInstanceSucceedsForSystemAdminEvenWhenNotOwner() {
        SeatInstanceDto request = new SeatInstanceDto(null, 1L, seatRef(1L), SeatStatus.AVAILABLE);
        when(flightClient.getFlightInstanceById(1L)).thenReturn(flightInstanceOwnedBy(42L));
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat(1L)));
        SeatInstance saved = new SeatInstance(1L, 1L, seat(1L), SeatStatus.AVAILABLE);
        when(seatInstanceRepository.save(any(SeatInstance.class))).thenReturn(saved);

        SeatInstanceDto result = seatInstanceService.createSeatInstance(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void createSeatInstanceThrowsWhenSeatMissing() {
        SeatInstanceDto request = new SeatInstanceDto(null, 1L, seatRef(99L), SeatStatus.AVAILABLE);
        when(flightClient.getFlightInstanceById(1L)).thenReturn(flightInstanceOwnedBy(42L));
        when(seatRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> seatInstanceService.createSeatInstance(request, 42L, "ROLE_AIRLINE_OWNER"));
        verify(seatInstanceRepository, never()).save(any());
    }

    @Test
    void getSeatInstanceByIdThrowsWhenMissing() {
        when(seatInstanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> seatInstanceService.getSeatInstanceById(99L));
    }

    @Test
    void getAllSeatInstancesMapsEveryRow() {
        when(seatInstanceRepository.findAll()).thenReturn(List.of(
                new SeatInstance(1L, 1L, seat(1L), SeatStatus.AVAILABLE),
                new SeatInstance(2L, 1L, seat(2L), SeatStatus.HELD)));

        List<SeatInstanceDto> result = seatInstanceService.getAllSeatInstances();

        assertEquals(2, result.size());
        assertEquals(SeatStatus.HELD, result.get(1).getStatus());
    }

    @Test
    void holdSeatFlipsAvailableToHeld() {
        SeatInstance seat = new SeatInstance(1L, 1L, null, SeatStatus.AVAILABLE);
        when(seatInstanceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));
        when(seatInstanceRepository.save(any(SeatInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatInstanceDto result = seatInstanceService.holdSeat(1L);

        assertEquals(SeatStatus.HELD, result.getStatus());
    }

    @Test
    void holdSeatThrowsWhenNotAvailable() {
        SeatInstance seat = new SeatInstance(1L, 1L, null, SeatStatus.BOOKED);
        when(seatInstanceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));

        assertThrows(SeatNotAvailableException.class, () -> seatInstanceService.holdSeat(1L));
        verify(seatInstanceRepository, never()).save(any());
    }

    @Test
    void releaseSeatFlipsHeldBackToAvailable() {
        SeatInstance seat = new SeatInstance(1L, 1L, null, SeatStatus.HELD);
        when(seatInstanceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));
        when(seatInstanceRepository.save(any(SeatInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatInstanceDto result = seatInstanceService.releaseSeat(1L);

        assertEquals(SeatStatus.AVAILABLE, result.getStatus());
    }

    @Test
    void releaseSeatIsIdempotentWhenAlreadyAvailable() {
        SeatInstance seat = new SeatInstance(1L, 1L, null, SeatStatus.AVAILABLE);
        when(seatInstanceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));

        SeatInstanceDto result = seatInstanceService.releaseSeat(1L);

        assertEquals(SeatStatus.AVAILABLE, result.getStatus());
        verify(seatInstanceRepository, never()).save(any());
    }

    @Test
    void releaseSeatThrowsWhenAlreadyBooked() {
        SeatInstance seat = new SeatInstance(1L, 1L, null, SeatStatus.BOOKED);
        when(seatInstanceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));

        assertThrows(SeatAlreadyBookedException.class, () -> seatInstanceService.releaseSeat(1L));
        verify(seatInstanceRepository, never()).save(any());
    }
}
