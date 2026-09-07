package com.services.service;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.common.dto.AirportDto;
import com.services.client.AircraftClient;
import com.services.client.AirlineClient;
import com.services.client.LocationClient;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;
import com.services.entity.FlightInstanceStatus;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.FlightInstanceRepository;
import com.services.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightInstanceRepository flightInstanceRepository;

    @Mock
    private AirlineClient airlineClient;

    @Mock
    private LocationClient locationClient;

    @Mock
    private AircraftClient aircraftClient;

    @InjectMocks
    private FlightService flightService;

    private AirlineDto airlineOwnedBy(Long ownerId) {
        return new AirlineDto(10L, "Air India", "AI", null, ownerId);
    }

    @Test
    void getFlightByIdThrowsWhenMissing() {
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightService.getFlightById(99L));
        verifyNoInteractions(airlineClient, locationClient);
    }

    @Test
    void getAllFlightsMakesExactlyOneBulkCallPerDependency() {
        Flight flight1 = new Flight(1L, 10L, "AI101", 100L, 100L);
        Flight flight2 = new Flight(2L, 20L, "6E202", 200L, 300L);
        when(flightRepository.findAll()).thenReturn(List.of(flight1, flight2));

        when(airlineClient.getAirlinesByIds(anyList())).thenReturn(List.of(
                new AirlineDto(10L, "Air India", "AI", null, 42L),
                new AirlineDto(20L, "IndiGo", "6E", null, 43L)));
        when(locationClient.getAirportsByIds(anyList())).thenReturn(List.of(
                new AirportDto(100L, "BOM", "Mumbai Airport", null),
                new AirportDto(200L, "DEL", "Delhi Airport", null),
                new AirportDto(300L, "BLR", "Bangalore Airport", null)));

        List<FlightDto> result = flightService.getAllFlights();

        assertEquals(2, result.size());
        assertEquals("Air India", result.get(0).getAirline().getName());
        assertEquals("Bangalore Airport", result.get(1).getArrivalAirport().getName());
        verify(airlineClient, times(1)).getAirlinesByIds(anyList());
        verify(locationClient, times(1)).getAirportsByIds(anyList());
    }

    @Test
    void getAllFlightInstancesDedupesSharedFlightBeforeEnriching() {
        Flight flight = new Flight(1L, 10L, "AI101", 100L, 100L);
        FlightInstance instance1 = new FlightInstance(1L, flight, LocalDateTime.now(), LocalDateTime.now(), FlightInstanceStatus.SCHEDULED, null, null);
        FlightInstance instance2 = new FlightInstance(2L, flight, LocalDateTime.now(), LocalDateTime.now(), FlightInstanceStatus.SCHEDULED, null, null);
        when(flightInstanceRepository.findAll()).thenReturn(List.of(instance1, instance2));

        when(airlineClient.getAirlinesByIds(anyList())).thenReturn(List.of(airlineOwnedBy(42L)));
        when(locationClient.getAirportsByIds(anyList())).thenReturn(List.of(new AirportDto(100L, "BOM", "Mumbai Airport", null)));

        List<FlightInstanceDto> result = flightService.getAllFlightInstances();

        assertEquals(2, result.size());
        assertEquals("Air India", result.get(0).getFlight().getAirline().getName());
        assertEquals("Air India", result.get(1).getFlight().getAirline().getName());
        // Both instances share one flight - enrichment must happen once, not once per instance.
        verify(airlineClient, times(1)).getAirlinesByIds(anyList());
        verify(locationClient, times(1)).getAirportsByIds(anyList());
        verifyNoInteractions(aircraftClient);
    }

    @Test
    void getAllFlightInstancesEnrichesAircraftInOneBulkCall() {
        Flight flight = new Flight(1L, 10L, "AI101", 100L, 100L);
        FlightInstance instance1 = new FlightInstance(1L, flight, LocalDateTime.now(), LocalDateTime.now(), FlightInstanceStatus.SCHEDULED, 500L, null);
        FlightInstance instance2 = new FlightInstance(2L, flight, LocalDateTime.now(), LocalDateTime.now(), FlightInstanceStatus.SCHEDULED, 500L, null);
        when(flightInstanceRepository.findAll()).thenReturn(List.of(instance1, instance2));

        when(airlineClient.getAirlinesByIds(anyList())).thenReturn(List.of(airlineOwnedBy(42L)));
        when(locationClient.getAirportsByIds(anyList())).thenReturn(List.of(new AirportDto(100L, "BOM", "Mumbai Airport", null)));
        when(aircraftClient.getAircraftByIds(anyList())).thenReturn(List.of(new AircraftDto(500L, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE", null)));

        List<FlightInstanceDto> result = flightService.getAllFlightInstances();

        assertEquals("VT-ABC", result.get(0).getAircraft().getRegistrationNumber());
        assertEquals("VT-ABC", result.get(1).getAircraft().getRegistrationNumber());
        verify(aircraftClient, times(1)).getAircraftByIds(anyList());
    }

    @Test
    void createFlightSavesAndEnrichesForOwningAirlineOwner() {
        Flight saved = new Flight(1L, 10L, "AI101", 100L, 100L);
        when(flightRepository.save(any(Flight.class))).thenReturn(saved);
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        when(locationClient.getAirportById(100L)).thenReturn(new AirportDto(100L, "BOM", "Mumbai Airport", null));

        FlightDto request = new FlightDto(null, "AI101", new AirlineDto(10L, null, null, null, null),
                new AirportDto(100L, null, null, null), new AirportDto(100L, null, null, null));

        FlightDto result = flightService.createFlight(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals("AI101", result.getFlightNumber());
    }

    @Test
    void createFlightThrowsForbiddenForCustomer() {
        FlightDto request = new FlightDto(null, "AI101", new AirlineDto(10L, null, null, null, null),
                new AirportDto(100L, null, null, null), new AirportDto(100L, null, null, null));

        assertThrows(ForbiddenException.class, () -> flightService.createFlight(request, 1L, "ROLE_CUSTOMER"));
        verify(flightRepository, never()).save(any());
        verifyNoInteractions(airlineClient, locationClient);
    }

    @Test
    void createFlightThrowsForbiddenForNonOwningAirlineOwner() {
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        FlightDto request = new FlightDto(null, "AI101", new AirlineDto(10L, null, null, null, null),
                new AirportDto(100L, null, null, null), new AirportDto(100L, null, null, null));

        assertThrows(ForbiddenException.class, () -> flightService.createFlight(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(flightRepository, never()).save(any());
    }

    @Test
    void createFlightSucceedsForSystemAdminEvenWhenNotOwner() {
        Flight saved = new Flight(1L, 10L, "AI101", 100L, 100L);
        when(flightRepository.save(any(Flight.class))).thenReturn(saved);
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        when(locationClient.getAirportById(100L)).thenReturn(new AirportDto(100L, "BOM", "Mumbai Airport", null));

        FlightDto request = new FlightDto(null, "AI101", new AirlineDto(10L, null, null, null, null),
                new AirportDto(100L, null, null, null), new AirportDto(100L, null, null, null));

        FlightDto result = flightService.createFlight(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals("AI101", result.getFlightNumber());
    }

    @Test
    void createFlightInstanceThrowsForbiddenForCustomer() {
        FlightDto flightRef = new FlightDto();
        flightRef.setId(1L);
        FlightInstanceDto request = new FlightInstanceDto();
        request.setFlight(flightRef);

        assertThrows(ForbiddenException.class, () -> flightService.createFlightInstance(request, 1L, "ROLE_CUSTOMER"));
        verifyNoInteractions(flightRepository, flightInstanceRepository);
    }

    @Test
    void createFlightInstanceThrowsForbiddenForNonOwningAirlineOwner() {
        Flight flight = new Flight(1L, 10L, "AI101", 100L, 100L);
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));

        FlightDto flightRef = new FlightDto();
        flightRef.setId(1L);
        FlightInstanceDto request = new FlightInstanceDto();
        request.setFlight(flightRef);

        assertThrows(ForbiddenException.class, () -> flightService.createFlightInstance(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(flightInstanceRepository, never()).save(any());
    }

    @Test
    void createFlightInstanceStampsAircraftIdWhenProvided() {
        Flight flight = new Flight(1L, 10L, "AI101", 100L, 100L);
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(flightInstanceRepository.save(any(FlightInstance.class))).thenAnswer(inv -> {
            FlightInstance instance = inv.getArgument(0);
            instance.setId(1L);
            return instance;
        });
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        when(locationClient.getAirportById(100L)).thenReturn(new AirportDto(100L, "BOM", "Mumbai Airport", null));
        when(aircraftClient.getAircraftById(500L)).thenReturn(new AircraftDto(500L, "VT-ABC", "737-800", "Boeing", 189, "ACTIVE", null));

        FlightDto flightRef = new FlightDto();
        flightRef.setId(1L);
        FlightInstanceDto request = new FlightInstanceDto();
        request.setFlight(flightRef);
        request.setStatus(FlightInstanceStatus.SCHEDULED);
        AircraftDto aircraftRef = new AircraftDto();
        aircraftRef.setId(500L);
        request.setAircraft(aircraftRef);

        FlightInstanceDto result = flightService.createFlightInstance(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals("VT-ABC", result.getAircraft().getRegistrationNumber());
    }
}
