package com.services.controller;

import com.common.dto.AirportDto;
import com.services.service.AirportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/airports")
public class AirportController {

    private final AirportService airportService;

    public AirportController(AirportService airportService) {
        this.airportService = airportService;
    }

    @PostMapping
    public ResponseEntity<AirportDto> createAirport(@RequestBody AirportDto airportDto,
                                                      @RequestHeader("X-User-Roles") String role) {
        AirportDto created = airportService.createAirport(airportDto, role);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AirportDto> getAirportById(@PathVariable Long id) {
        return ResponseEntity.ok(airportService.getAirportById(id));
    }

    @GetMapping
    public ResponseEntity<List<AirportDto>> getAllAirports(@RequestParam(required = false) List<Long> ids) {
        List<AirportDto> airports = (ids == null || ids.isEmpty())
                ? airportService.getAllAirports()
                : airportService.getAirportsByIds(ids);
        return ResponseEntity.ok(airports);
    }
}
