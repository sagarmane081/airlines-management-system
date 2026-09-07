package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.FlightClient;
import com.services.dto.AncillaryDto;
import com.services.dto.FlightAncillaryDto;
import com.services.dto.FlightOwnerView;
import com.services.entity.Ancillary;
import com.services.entity.AncillaryType;
import com.services.entity.FlightAncillary;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.AncillaryRepository;
import com.services.repository.FlightAncillaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightAncillaryServiceTest {

    @Mock
    private FlightAncillaryRepository flightAncillaryRepository;

    @Mock
    private AncillaryRepository ancillaryRepository;

    @Mock
    private FlightClient flightClient;

    @InjectMocks
    private FlightAncillaryService flightAncillaryService;

    private Ancillary ancillaryOfAirline(Long airlineId) {
        return new Ancillary(1L, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, airlineId);
    }

    private FlightOwnerView flightOwnedBy(Long airlineId, Long ownerId) {
        return new FlightOwnerView(5L, new AirlineDto(airlineId, "Air India", "AI", null, ownerId));
    }

    private AncillaryDto ancillaryRef(Long id) {
        AncillaryDto dto = new AncillaryDto();
        dto.setId(id);
        return dto;
    }

    private FlightAncillaryDto request() {
        return new FlightAncillaryDto(null, 5L, ancillaryRef(1L), BigDecimal.valueOf(35), true);
    }

    @Test
    void createFlightAncillarySavesAndReturnsDtoForOwningAirlineOwner() {
        when(ancillaryRepository.findById(1L)).thenReturn(Optional.of(ancillaryOfAirline(10L)));
        when(flightClient.getFlightById(5L)).thenReturn(flightOwnedBy(10L, 42L));
        FlightAncillary saved = new FlightAncillary(1L, 5L, ancillaryOfAirline(10L), BigDecimal.valueOf(35), true);
        when(flightAncillaryRepository.save(any(FlightAncillary.class))).thenReturn(saved);

        FlightAncillaryDto result = flightAncillaryService.createFlightAncillary(request(), 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(BigDecimal.valueOf(35), result.getPrice());
        assertTrue(result.getAvailable());
    }

    @Test
    void createFlightAncillaryThrowsForbiddenForCustomer() {
        assertThrows(ForbiddenException.class, () -> flightAncillaryService.createFlightAncillary(request(), 1L, "ROLE_CUSTOMER"));
        verify(flightAncillaryRepository, never()).save(any());
        verifyNoInteractions(ancillaryRepository, flightClient);
    }

    @Test
    void createFlightAncillaryThrowsForbiddenForNonOwningAirlineOwner() {
        when(ancillaryRepository.findById(1L)).thenReturn(Optional.of(ancillaryOfAirline(10L)));
        when(flightClient.getFlightById(5L)).thenReturn(flightOwnedBy(10L, 42L));

        assertThrows(ForbiddenException.class, () -> flightAncillaryService.createFlightAncillary(request(), 999L, "ROLE_AIRLINE_OWNER"));
        verify(flightAncillaryRepository, never()).save(any());
    }

    @Test
    void createFlightAncillarySucceedsForSystemAdminEvenWhenNotOwner() {
        when(ancillaryRepository.findById(1L)).thenReturn(Optional.of(ancillaryOfAirline(10L)));
        when(flightClient.getFlightById(5L)).thenReturn(flightOwnedBy(10L, 42L));
        FlightAncillary saved = new FlightAncillary(1L, 5L, ancillaryOfAirline(10L), BigDecimal.valueOf(35), true);
        when(flightAncillaryRepository.save(any(FlightAncillary.class))).thenReturn(saved);

        FlightAncillaryDto result = flightAncillaryService.createFlightAncillary(request(), 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void createFlightAncillaryThrowsWhenAncillaryBelongsToDifferentAirlineEvenForAdmin() {
        when(ancillaryRepository.findById(1L)).thenReturn(Optional.of(ancillaryOfAirline(20L)));
        when(flightClient.getFlightById(5L)).thenReturn(flightOwnedBy(10L, 42L));

        assertThrows(ForbiddenException.class, () -> flightAncillaryService.createFlightAncillary(request(), 999L, "ROLE_SYSTEM_ADMIN"));
        verify(flightAncillaryRepository, never()).save(any());
    }

    @Test
    void createFlightAncillaryThrowsWhenAncillaryMissing() {
        when(ancillaryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightAncillaryService.createFlightAncillary(request(), 42L, "ROLE_AIRLINE_OWNER"));
        verify(flightAncillaryRepository, never()).save(any());
        verifyNoInteractions(flightClient);
    }

    @Test
    void getFlightAncillaryByIdThrowsWhenMissing() {
        when(flightAncillaryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightAncillaryService.getFlightAncillaryById(99L));
    }

    @Test
    void getAllFlightAncillariesMapsEveryRow() {
        when(flightAncillaryRepository.findAll()).thenReturn(List.of(
                new FlightAncillary(1L, 5L, ancillaryOfAirline(10L), BigDecimal.valueOf(35), true),
                new FlightAncillary(2L, 6L, ancillaryOfAirline(10L), BigDecimal.valueOf(40), false)));

        List<FlightAncillaryDto> result = flightAncillaryService.getAllFlightAncillaries();

        assertEquals(2, result.size());
        assertFalse(result.get(1).getAvailable());
    }
}
