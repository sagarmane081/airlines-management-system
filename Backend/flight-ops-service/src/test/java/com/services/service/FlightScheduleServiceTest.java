package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.AirlineClient;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.dto.FlightScheduleDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;
import com.services.entity.FlightSchedule;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.FlightInstanceRepository;
import com.services.repository.FlightRepository;
import com.services.repository.FlightScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightScheduleServiceTest {

    @Mock
    private FlightScheduleRepository flightScheduleRepository;

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightInstanceRepository flightInstanceRepository;

    @Mock
    private AirlineClient airlineClient;

    @Mock
    private FlightService flightService;

    @InjectMocks
    private FlightScheduleService flightScheduleService;

    private Flight flight() {
        return new Flight(1L, 10L, "AI101", 100L, 200L);
    }

    private AirlineDto airlineOwnedBy(Long ownerId) {
        return new AirlineDto(10L, "Air India", "AI", null, ownerId);
    }

    private FlightDto flightRef(Long id) {
        FlightDto dto = new FlightDto();
        dto.setId(id);
        return dto;
    }

    private FlightScheduleDto request() {
        return new FlightScheduleDto(null, flightRef(1L), LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
    }

    @Test
    void createFlightScheduleSavesAndReturnsDtoForOwningAirlineOwner() {
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight()));
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        FlightSchedule saved = new FlightSchedule(1L, flight(), LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14), Set.of(DayOfWeek.MONDAY));
        when(flightScheduleRepository.save(any(FlightSchedule.class))).thenReturn(saved);
        when(flightService.enrichFlight(any(Flight.class))).thenReturn(new FlightDto(1L, "AI101", null, null, null));

        FlightScheduleDto result = flightScheduleService.createFlightSchedule(request(), 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals("AI101", result.getFlight().getFlightNumber());
    }

    @Test
    void createFlightScheduleThrowsForbiddenForCustomer() {
        assertThrows(ForbiddenException.class, () -> flightScheduleService.createFlightSchedule(request(), 1L, "ROLE_CUSTOMER"));
        verify(flightScheduleRepository, never()).save(any());
        verifyNoInteractions(flightRepository, airlineClient);
    }

    @Test
    void createFlightScheduleThrowsForbiddenForNonOwningAirlineOwner() {
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight()));
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> flightScheduleService.createFlightSchedule(request(), 999L, "ROLE_AIRLINE_OWNER"));
        verify(flightScheduleRepository, never()).save(any());
    }

    @Test
    void getFlightScheduleByIdThrowsWhenMissing() {
        when(flightScheduleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightScheduleService.getFlightScheduleById(99L));
    }

    @Test
    void generateInstancesCreatesOneInstancePerMatchingOperatingDay() {
        FlightSchedule schedule = new FlightSchedule(1L, flight(), LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        // 2026-01-01 is a Thursday; Mon/Wed/Fri in [Jan 1, Jan 14] are: 2, 5, 7, 9, 12, 14 = 6 dates.
        when(flightScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        when(flightInstanceRepository.existsByFlightScheduleIdAndDepartureTime(anyLong(), any(LocalDateTime.class))).thenReturn(false);
        when(flightInstanceRepository.save(any(FlightInstance.class))).thenAnswer(inv -> {
            FlightInstance instance = inv.getArgument(0);
            instance.setId((long) instance.getDepartureTime().getDayOfMonth());
            return instance;
        });
        when(flightService.enrichFlight(any(Flight.class))).thenReturn(new FlightDto(1L, "AI101", null, null, null));

        List<FlightInstanceDto> result = flightScheduleService.generateInstances(1L, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(6, result.size());
        verify(flightInstanceRepository, times(6)).save(any(FlightInstance.class));
    }

    @Test
    void generateInstancesSkipsDatesAlreadyGenerated() {
        FlightSchedule schedule = new FlightSchedule(1L, flight(), LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7),
                Set.of(DayOfWeek.MONDAY));
        // Only one Monday (Jan 5) in this range.
        when(flightScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        when(flightInstanceRepository.existsByFlightScheduleIdAndDepartureTime(1L, LocalDateTime.of(2026, 1, 5, 10, 0)))
                .thenReturn(true);
        when(flightService.enrichFlight(any(Flight.class))).thenReturn(new FlightDto(1L, "AI101", null, null, null));

        List<FlightInstanceDto> result = flightScheduleService.generateInstances(1L, 42L, "ROLE_AIRLINE_OWNER");

        assertTrue(result.isEmpty());
        verify(flightInstanceRepository, never()).save(any());
    }

    @Test
    void generateInstancesThrowsForbiddenForNonOwningAirlineOwner() {
        FlightSchedule schedule = new FlightSchedule(1L, flight(), LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), Set.of(DayOfWeek.MONDAY));
        when(flightScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> flightScheduleService.generateInstances(1L, 999L, "ROLE_AIRLINE_OWNER"));
        verify(flightInstanceRepository, never()).save(any());
    }
}
