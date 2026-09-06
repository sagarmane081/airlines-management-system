package com.services.controller;

import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.service.FlightService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    @PostMapping("/api/flights")
    public ResponseEntity<FlightDto> createFlight(@RequestBody FlightDto flightDto) {
        return new ResponseEntity<>(flightService.createFlight(flightDto), HttpStatus.CREATED);
    }

    @GetMapping("/api/flights/{id}")
    public ResponseEntity<FlightDto> getFlightById(@PathVariable Long id) {
        return ResponseEntity.ok(flightService.getFlightById(id));
    }

    @GetMapping("/api/flights")
    public ResponseEntity<List<FlightDto>> getAllFlights() {
        return ResponseEntity.ok(flightService.getAllFlights());
    }

    @PostMapping("/api/flight-instances")
    public ResponseEntity<FlightInstanceDto> createFlightInstance(@RequestBody FlightInstanceDto instanceDto) {
        return new ResponseEntity<>(flightService.createFlightInstance(instanceDto), HttpStatus.CREATED);
    }

    @GetMapping("/api/flight-instances/{id}")
    public ResponseEntity<FlightInstanceDto> getFlightInstanceById(@PathVariable Long id) {
        return ResponseEntity.ok(flightService.getFlightInstanceById(id));
    }

    @GetMapping("/api/flight-instances")
    public ResponseEntity<List<FlightInstanceDto>> getAllFlightInstances() {
        return ResponseEntity.ok(flightService.getAllFlightInstances());
    }
}