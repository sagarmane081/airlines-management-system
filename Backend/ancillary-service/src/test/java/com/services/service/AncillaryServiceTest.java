package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.AirlineClient;
import com.services.dto.AncillaryDto;
import com.services.entity.Ancillary;
import com.services.entity.AncillaryType;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.AncillaryRepository;
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
class AncillaryServiceTest {

    @Mock
    private AncillaryRepository ancillaryRepository;

    @Mock
    private AirlineClient airlineClient;

    @InjectMocks
    private AncillaryService ancillaryService;

    private AirlineDto airlineOwnedBy(Long ownerId) {
        return new AirlineDto(10L, "Air India", "AI", null, ownerId);
    }

    @Test
    void createAncillarySavesAndReturnsDtoForOwningAirlineOwner() {
        AncillaryDto request = new AncillaryDto(null, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, 10L);
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        Ancillary saved = new Ancillary(1L, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, 10L);
        when(ancillaryRepository.save(any(Ancillary.class))).thenReturn(saved);

        AncillaryDto result = ancillaryService.createAncillary(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(AncillaryType.BAGGAGE, result.getType());
    }

    @Test
    void createAncillaryThrowsForbiddenForCustomer() {
        AncillaryDto request = new AncillaryDto(null, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, 10L);

        assertThrows(ForbiddenException.class, () -> ancillaryService.createAncillary(request, 1L, "ROLE_CUSTOMER"));
        verify(ancillaryRepository, never()).save(any());
        verifyNoInteractions(airlineClient);
    }

    @Test
    void createAncillaryThrowsForbiddenForNonOwningAirlineOwner() {
        AncillaryDto request = new AncillaryDto(null, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, 10L);
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> ancillaryService.createAncillary(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(ancillaryRepository, never()).save(any());
    }

    @Test
    void createAncillarySucceedsForSystemAdminEvenWhenNotOwner() {
        AncillaryDto request = new AncillaryDto(null, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, 10L);
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        Ancillary saved = new Ancillary(1L, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, 10L);
        when(ancillaryRepository.save(any(Ancillary.class))).thenReturn(saved);

        AncillaryDto result = ancillaryService.createAncillary(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void getAncillaryByIdReturnsDtoWhenFound() {
        Ancillary ancillary = new Ancillary(1L, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, 10L);
        when(ancillaryRepository.findById(1L)).thenReturn(Optional.of(ancillary));

        AncillaryDto result = ancillaryService.getAncillaryById(1L);

        assertEquals("Extra bag", result.getName());
    }

    @Test
    void getAncillaryByIdThrowsWhenMissing() {
        when(ancillaryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ancillaryService.getAncillaryById(99L));
    }

    @Test
    void getAllAncillariesMapsEveryRow() {
        when(ancillaryRepository.findAll()).thenReturn(List.of(
                new Ancillary(1L, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE, 10L),
                new Ancillary(2L, "Wifi", "In-flight wifi", BigDecimal.valueOf(15), AncillaryType.WIFI, 10L)));

        List<AncillaryDto> result = ancillaryService.getAllAncillaries();

        assertEquals(2, result.size());
        assertEquals(AncillaryType.WIFI, result.get(1).getType());
    }
}
