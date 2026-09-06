package com.services.controller;

import com.services.dto.SeatInstanceDto;
import com.services.service.SeatInstanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seat-instances")
public class SeatInstanceController {

    private final SeatInstanceService seatInstanceService;

    public SeatInstanceController(SeatInstanceService seatInstanceService) {
        this.seatInstanceService = seatInstanceService;
    }

    @PostMapping
    public ResponseEntity<SeatInstanceDto> createSeatInstance(@RequestBody SeatInstanceDto seatInstanceDto,
                                                               @RequestHeader("X-User-Id") Long requesterId,
                                                               @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(seatInstanceService.createSeatInstance(seatInstanceDto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatInstanceDto> getSeatInstanceById(@PathVariable Long id) {
        return ResponseEntity.ok(seatInstanceService.getSeatInstanceById(id));
    }

    @GetMapping
    public ResponseEntity<List<SeatInstanceDto>> getAllSeatInstances() {
        return ResponseEntity.ok(seatInstanceService.getAllSeatInstances());
    }

    @PostMapping("/{id}/hold")
    public ResponseEntity<SeatInstanceDto> holdSeat(@PathVariable Long id) {
        return ResponseEntity.ok(seatInstanceService.holdSeat(id));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<SeatInstanceDto> releaseSeat(@PathVariable Long id) {
        return ResponseEntity.ok(seatInstanceService.releaseSeat(id));
    }
}
