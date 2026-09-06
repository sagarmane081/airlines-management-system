package com.services.controller;

import com.common.dto.AirlineDto;
import com.services.service.AirlineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/airlines")
public class AirlineController {

    private final AirlineService airlineService;

    public AirlineController(AirlineService airlineService) {
        this.airlineService = airlineService;
    }

    @PostMapping
    public ResponseEntity<AirlineDto> createAirline(@RequestBody AirlineDto airlineDto,
                                                     @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(airlineService.createAirline(airlineDto, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AirlineDto> getAirlineById(@PathVariable Long id) {
        return ResponseEntity.ok(airlineService.getAirlineById(id));
    }

    @GetMapping
    public ResponseEntity<List<AirlineDto>> getAllAirlines(@RequestParam(required = false) List<Long> ids) {
        List<AirlineDto> airlines = (ids == null || ids.isEmpty())
                ? airlineService.getAllAirlines()
                : airlineService.getAirlinesByIds(ids);
        return ResponseEntity.ok(airlines);
    }
}