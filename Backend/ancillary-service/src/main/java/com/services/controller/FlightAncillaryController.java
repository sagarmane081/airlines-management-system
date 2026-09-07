package com.services.controller;

import com.services.dto.FlightAncillaryDto;
import com.services.service.FlightAncillaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flight-ancillaries")
public class FlightAncillaryController {

    private final FlightAncillaryService flightAncillaryService;

    public FlightAncillaryController(FlightAncillaryService flightAncillaryService) {
        this.flightAncillaryService = flightAncillaryService;
    }

    @PostMapping
    public ResponseEntity<FlightAncillaryDto> createFlightAncillary(@RequestBody FlightAncillaryDto dto,
                                                                      @RequestHeader("X-User-Id") Long requesterId,
                                                                      @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(flightAncillaryService.createFlightAncillary(dto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightAncillaryDto> getFlightAncillaryById(@PathVariable Long id) {
        return ResponseEntity.ok(flightAncillaryService.getFlightAncillaryById(id));
    }

    @GetMapping
    public ResponseEntity<List<FlightAncillaryDto>> getAllFlightAncillaries() {
        return ResponseEntity.ok(flightAncillaryService.getAllFlightAncillaries());
    }
}
