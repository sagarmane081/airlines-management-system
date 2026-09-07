package com.services.controller;

import com.services.dto.FlightInstanceDto;
import com.services.dto.FlightScheduleDto;
import com.services.service.FlightScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flight-schedules")
public class FlightScheduleController {

    private final FlightScheduleService flightScheduleService;

    public FlightScheduleController(FlightScheduleService flightScheduleService) {
        this.flightScheduleService = flightScheduleService;
    }

    @PostMapping
    public ResponseEntity<FlightScheduleDto> createFlightSchedule(@RequestBody FlightScheduleDto scheduleDto,
                                                                    @RequestHeader("X-User-Id") Long requesterId,
                                                                    @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(flightScheduleService.createFlightSchedule(scheduleDto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightScheduleDto> getFlightScheduleById(@PathVariable Long id) {
        return ResponseEntity.ok(flightScheduleService.getFlightScheduleById(id));
    }

    @GetMapping
    public ResponseEntity<List<FlightScheduleDto>> getAllFlightSchedules() {
        return ResponseEntity.ok(flightScheduleService.getAllFlightSchedules());
    }

    @PostMapping("/{id}/generate-instances")
    public ResponseEntity<List<FlightInstanceDto>> generateInstances(@PathVariable Long id,
                                                                       @RequestHeader("X-User-Id") Long requesterId,
                                                                       @RequestHeader("X-User-Roles") String role) {
        return ResponseEntity.ok(flightScheduleService.generateInstances(id, requesterId, role));
    }
}
