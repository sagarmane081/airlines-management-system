package com.services.controller;

import com.services.dto.SeatMapDto;
import com.services.service.SeatMapService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seat-maps")
public class SeatMapController {

    private final SeatMapService seatMapService;

    public SeatMapController(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    @PostMapping
    public ResponseEntity<SeatMapDto> createSeatMap(@RequestBody SeatMapDto seatMapDto,
                                                     @RequestHeader("X-User-Id") Long requesterId,
                                                     @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(seatMapService.createSeatMap(seatMapDto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatMapDto> getSeatMapById(@PathVariable Long id) {
        return ResponseEntity.ok(seatMapService.getSeatMapById(id));
    }

    @GetMapping
    public ResponseEntity<List<SeatMapDto>> getAllSeatMaps() {
        return ResponseEntity.ok(seatMapService.getAllSeatMaps());
    }
}
