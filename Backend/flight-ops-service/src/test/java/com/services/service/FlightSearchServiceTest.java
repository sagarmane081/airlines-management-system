package com.services.service;

import com.common.dto.FareDto;
import com.services.client.FareClient;
import com.services.dto.FlightDto;
import com.services.dto.FlightSearchResultDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;
import com.services.entity.FlightInstanceStatus;
import com.services.repository.FlightInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightSearchServiceTest {

    @Mock
    private FlightInstanceRepository flightInstanceRepository;

    @Mock
    private FareClient fareClient;

    @Mock
    private FlightService flightService;

    @InjectMocks
    private FlightSearchService flightSearchService;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 5);

    private Flight flight() {
        return new Flight(1L, 10L, "AI101", 100L, 200L);
    }

    private FlightInstance instance(Long id, LocalDateTime departureTime) {
        return new FlightInstance(id, flight(), departureTime, departureTime.plusHours(2), FlightInstanceStatus.SCHEDULED, null, null);
    }

    private void stubEnrichment() {
        when(flightService.enrichFlights(anyList())).thenReturn(List.of(new FlightDto(1L, "AI101", null, null, null)));
    }

    @Test
    void searchReturnsOneRowPerFareWhenNoFiltersActive() {
        when(flightInstanceRepository.findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                eq(100L), eq(200L), any(), any(), eq(FlightInstanceStatus.SCHEDULED)))
                .thenReturn(List.of(instance(1L, DATE.atTime(10, 0))));
        stubEnrichment();
        when(fareClient.getFaresByFlightIds(List.of(1L))).thenReturn(List.of(
                new FareDto(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null),
                new FareDto(2L, 1L, "BUSINESS", BigDecimal.valueOf(800), "USD", null, null)));

        List<FlightSearchResultDto> results = flightSearchService.searchFlights(100L, 200L, DATE, null, null, null, "departureTime");

        assertEquals(2, results.size());
    }

    @Test
    void searchFiltersByCabinClass() {
        when(flightInstanceRepository.findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                eq(100L), eq(200L), any(), any(), eq(FlightInstanceStatus.SCHEDULED)))
                .thenReturn(List.of(instance(1L, DATE.atTime(10, 0))));
        stubEnrichment();
        when(fareClient.getFaresByFlightIds(List.of(1L))).thenReturn(List.of(
                new FareDto(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null),
                new FareDto(2L, 1L, "BUSINESS", BigDecimal.valueOf(800), "USD", null, null)));

        List<FlightSearchResultDto> results = flightSearchService.searchFlights(100L, 200L, DATE, "ECONOMY", null, null, "departureTime");

        assertEquals(1, results.size());
        assertEquals("ECONOMY", results.get(0).getFare().getCabinClass());
    }

    @Test
    void searchFiltersByMinPrice() {
        when(flightInstanceRepository.findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                eq(100L), eq(200L), any(), any(), eq(FlightInstanceStatus.SCHEDULED)))
                .thenReturn(List.of(instance(1L, DATE.atTime(10, 0))));
        stubEnrichment();
        when(fareClient.getFaresByFlightIds(List.of(1L))).thenReturn(List.of(
                new FareDto(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null),
                new FareDto(2L, 1L, "BUSINESS", BigDecimal.valueOf(800), "USD", null, null)));

        List<FlightSearchResultDto> results = flightSearchService.searchFlights(100L, 200L, DATE, null, BigDecimal.valueOf(500), null, "departureTime");

        assertEquals(1, results.size());
        assertEquals("BUSINESS", results.get(0).getFare().getCabinClass());
    }

    @Test
    void searchIncludesUnpricedFlightWithNullFareWhenNoFilterActive() {
        when(flightInstanceRepository.findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                eq(100L), eq(200L), any(), any(), eq(FlightInstanceStatus.SCHEDULED)))
                .thenReturn(List.of(instance(1L, DATE.atTime(10, 0))));
        stubEnrichment();
        when(fareClient.getFaresByFlightIds(List.of(1L))).thenReturn(List.of());

        List<FlightSearchResultDto> results = flightSearchService.searchFlights(100L, 200L, DATE, null, null, null, "departureTime");

        assertEquals(1, results.size());
        assertNull(results.get(0).getFare());
    }

    @Test
    void searchExcludesUnpricedFlightWhenAPriceFilterIsActive() {
        when(flightInstanceRepository.findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                eq(100L), eq(200L), any(), any(), eq(FlightInstanceStatus.SCHEDULED)))
                .thenReturn(List.of(instance(1L, DATE.atTime(10, 0))));
        stubEnrichment();
        when(fareClient.getFaresByFlightIds(List.of(1L))).thenReturn(List.of());

        List<FlightSearchResultDto> results = flightSearchService.searchFlights(100L, 200L, DATE, "ECONOMY", null, null, "departureTime");

        assertTrue(results.isEmpty());
    }

    @Test
    void searchReturnsEmptyWithoutCallingDependenciesWhenNoInstancesMatch() {
        when(flightInstanceRepository.findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                eq(100L), eq(200L), any(), any(), eq(FlightInstanceStatus.SCHEDULED)))
                .thenReturn(List.of());

        List<FlightSearchResultDto> results = flightSearchService.searchFlights(100L, 200L, DATE, null, null, null, "departureTime");

        assertTrue(results.isEmpty());
        verifyNoInteractions(fareClient, flightService);
    }

    @Test
    void searchSortsByPriceAscendingWhenRequested() {
        when(flightInstanceRepository.findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                eq(100L), eq(200L), any(), any(), eq(FlightInstanceStatus.SCHEDULED)))
                .thenReturn(List.of(instance(1L, DATE.atTime(10, 0))));
        stubEnrichment();
        when(fareClient.getFaresByFlightIds(List.of(1L))).thenReturn(List.of(
                new FareDto(1L, 1L, "BUSINESS", BigDecimal.valueOf(800), "USD", null, null),
                new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD", null, null)));

        List<FlightSearchResultDto> results = flightSearchService.searchFlights(100L, 200L, DATE, null, null, null, "price");

        assertEquals(BigDecimal.valueOf(250), results.get(0).getFare().getPrice());
        assertEquals(BigDecimal.valueOf(800), results.get(1).getFare().getPrice());
    }

    @Test
    void searchSortsByDepartureTimeByDefault() {
        FlightInstance later = instance(1L, DATE.atTime(18, 0));
        FlightInstance earlier = instance(2L, DATE.atTime(6, 0));
        when(flightInstanceRepository.findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
                eq(100L), eq(200L), any(), any(), eq(FlightInstanceStatus.SCHEDULED)))
                .thenReturn(List.of(later, earlier));
        stubEnrichment();
        when(fareClient.getFaresByFlightIds(List.of(1L))).thenReturn(List.of());

        List<FlightSearchResultDto> results = flightSearchService.searchFlights(100L, 200L, DATE, null, null, null, "departureTime");

        assertEquals(2L, results.get(0).getFlightInstance().getId());
        assertEquals(1L, results.get(1).getFlightInstance().getId());
    }
}
