package com.services.controller;

import com.services.dto.SeatDto;
import com.services.service.SeatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping
    public ResponseEntity<SeatDto> createSeat(@RequestBody SeatDto seatDto,
                                               @RequestHeader("X-User-Id") Long requesterId,
                                               @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(seatService.createSeat(seatDto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatDto> getSeatById(@PathVariable Long id) {
        return ResponseEntity.ok(seatService.getSeatById(id));
    }

    @GetMapping
    public ResponseEntity<List<SeatDto>> getAllSeats() {
        return ResponseEntity.ok(seatService.getAllSeats());
    }
}
