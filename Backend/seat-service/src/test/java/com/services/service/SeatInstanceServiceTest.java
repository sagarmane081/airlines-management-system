package com.services.service;

import com.services.dto.SeatInstanceDto;
import com.services.entity.SeatInstance;
import com.services.entity.SeatStatus;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.exception.SeatNotAvailableException;
import com.services.repository.SeatInstanceRepository;
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

    @InjectMocks
    private SeatInstanceService seatInstanceService;

    @Test
    void createSeatInstanceSavesAndReturnsDtoForAirlineOwner() {
        SeatInstanceDto request = new SeatInstanceDto(null, 1L, "12A", "ECONOMY", SeatStatus.AVAILABLE);
        SeatInstance saved = new SeatInstance(1L, 1L, "12A", "ECONOMY", SeatStatus.AVAILABLE);
        when(seatInstanceRepository.save(any(SeatInstance.class))).thenReturn(saved);

        SeatInstanceDto result = seatInstanceService.createSeatInstance(request, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(SeatStatus.AVAILABLE, result.getStatus());
    }

    @Test
    void createSeatInstanceThrowsForbiddenForCustomer() {
        SeatInstanceDto request = new SeatInstanceDto(null, 1L, "12A", "ECONOMY", SeatStatus.AVAILABLE);

        assertThrows(ForbiddenException.class, () -> seatInstanceService.createSeatInstance(request, "ROLE_CUSTOMER"));
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
                new SeatInstance(1L, 1L, "12A", "ECONOMY", SeatStatus.AVAILABLE),
                new SeatInstance(2L, 1L, "12B", "ECONOMY", SeatStatus.HELD)));

        List<SeatInstanceDto> result = seatInstanceService.getAllSeatInstances();

        assertEquals(2, result.size());
        assertEquals(SeatStatus.HELD, result.get(1).getStatus());
    }

    @Test
    void holdSeatFlipsAvailableToHeld() {
        SeatInstance seat = new SeatInstance(1L, 1L, "12A", "ECONOMY", SeatStatus.AVAILABLE);
        when(seatInstanceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));
        when(seatInstanceRepository.save(any(SeatInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatInstanceDto result = seatInstanceService.holdSeat(1L);

        assertEquals(SeatStatus.HELD, result.getStatus());
    }

    @Test
    void holdSeatThrowsWhenNotAvailable() {
        SeatInstance seat = new SeatInstance(1L, 1L, "12A", "ECONOMY", SeatStatus.BOOKED);
        when(seatInstanceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(seat));

        assertThrows(SeatNotAvailableException.class, () -> seatInstanceService.holdSeat(1L));
        verify(seatInstanceRepository, never()).save(any());
    }
}
