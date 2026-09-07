package com.services.service;

import com.common.dto.AirlineDto;
import com.common.dto.BaggagePolicyDto;
import com.common.dto.FareDto;
import com.common.dto.FareRulesDto;
import com.services.client.FlightClient;
import com.services.dto.FlightOwnerView;
import com.services.entity.Fare;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.FareRepository;
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
class FareServiceTest {

    @Mock
    private FareRepository fareRepository;

    @Mock
    private FlightClient flightClient;

    @InjectMocks
    private FareService fareService;

    private FlightOwnerView flightOwnedBy(Long ownerId) {
        return new FlightOwnerView(1L, new AirlineDto(10L, "Air India", "AI", null, ownerId));
    }

    @Test
    void createFareSavesAndReturnsDtoForOwningAirlineOwner() {
        FareDto request = new FareDto(null, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null);
        when(flightClient.getFlightById(1L)).thenReturn(flightOwnedBy(42L));
        Fare saved = new Fare(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null);
        when(fareRepository.save(any(Fare.class))).thenReturn(saved);

        FareDto result = fareService.createFare(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(BigDecimal.valueOf(250), result.getPrice());
    }

    @Test
    void createFareThrowsForbiddenForCustomer() {
        FareDto request = new FareDto(null, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null);

        assertThrows(ForbiddenException.class, () -> fareService.createFare(request, 1L, "ROLE_CUSTOMER"));
        verify(fareRepository, never()).save(any());
        verifyNoInteractions(flightClient);
    }

    @Test
    void createFareThrowsForbiddenForNonOwningAirlineOwner() {
        FareDto request = new FareDto(null, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null);
        when(flightClient.getFlightById(1L)).thenReturn(flightOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> fareService.createFare(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(fareRepository, never()).save(any());
    }

    @Test
    void createFareSucceedsForSystemAdminEvenWhenNotOwner() {
        FareDto request = new FareDto(null, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null);
        when(flightClient.getFlightById(1L)).thenReturn(flightOwnedBy(42L));
        Fare saved = new Fare(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null);
        when(fareRepository.save(any(Fare.class))).thenReturn(saved);

        FareDto result = fareService.createFare(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void createFareLinksFareRulesAndBaggagePolicyWhenProvided() {
        FareRulesDto fareRulesDto = new FareRulesDto(null, true, false, BigDecimal.valueOf(50), null, 24, null);
        BaggagePolicyDto baggagePolicyDto = new BaggagePolicyDto(null, BigDecimal.valueOf(7), BigDecimal.valueOf(23), 1, BigDecimal.valueOf(10));
        FareDto request = new FareDto(null, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", fareRulesDto, baggagePolicyDto);
        when(flightClient.getFlightById(1L)).thenReturn(flightOwnedBy(42L));
        when(fareRepository.save(any(Fare.class))).thenAnswer(inv -> inv.getArgument(0));

        FareDto result = fareService.createFare(request, 42L, "ROLE_AIRLINE_OWNER");

        assertTrue(result.getFareRules().getRefundable());
        assertEquals(24, result.getFareRules().getRefundDeadlineHours());
        assertEquals(BigDecimal.valueOf(10), result.getBaggagePolicy().getExtraBaggageFeePerKg());
    }

    @Test
    void createFareLeavesFareRulesAndBaggagePolicyNullWhenNotProvided() {
        FareDto request = new FareDto(null, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null);
        when(flightClient.getFlightById(1L)).thenReturn(flightOwnedBy(42L));
        when(fareRepository.save(any(Fare.class))).thenAnswer(inv -> inv.getArgument(0));

        FareDto result = fareService.createFare(request, 42L, "ROLE_AIRLINE_OWNER");

        assertNull(result.getFareRules());
        assertNull(result.getBaggagePolicy());
    }

    @Test
    void getFareByIdReturnsDtoWhenFound() {
        Fare fare = new Fare(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null);
        when(fareRepository.findById(1L)).thenReturn(Optional.of(fare));

        FareDto result = fareService.getFareById(1L);

        assertEquals("ECONOMY", result.getCabinClass());
    }

    @Test
    void getFareByIdThrowsWhenMissing() {
        when(fareRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> fareService.getFareById(99L));
    }

    @Test
    void getAllFaresMapsEveryRow() {
        when(fareRepository.findAll()).thenReturn(List.of(
                new Fare(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null),
                new Fare(2L, 1L, "BUSINESS", BigDecimal.valueOf(800), "USD", null, null)));

        List<FareDto> result = fareService.getAllFares();

        assertEquals(2, result.size());
        assertEquals("BUSINESS", result.get(1).getCabinClass());
    }
}
