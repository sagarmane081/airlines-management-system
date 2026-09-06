package com.services.controller;

import com.common.dto.AircraftDto;
import com.services.service.AircraftService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/aircrafts")
public class AircraftController {

    private final AircraftService aircraftService;

    public AircraftController(AircraftService aircraftService) {
        this.aircraftService = aircraftService;
    }

    @PostMapping
    public ResponseEntity<AircraftDto> createAircraft(@RequestBody AircraftDto aircraftDto,
                                                        @RequestHeader("X-User-Id") Long requesterId,
                                                        @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(aircraftService.createAircraft(aircraftDto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AircraftDto> getAircraftById(@PathVariable Long id) {
        return ResponseEntity.ok(aircraftService.getAircraftById(id));
    }

    @GetMapping
    public ResponseEntity<List<AircraftDto>> getAllAircraft(@RequestParam(required = false) List<Long> ids) {
        List<AircraftDto> aircraft = (ids == null || ids.isEmpty())
                ? aircraftService.getAllAircraft()
                : aircraftService.getAircraftByIds(ids);
        return ResponseEntity.ok(aircraft);
    }
}
