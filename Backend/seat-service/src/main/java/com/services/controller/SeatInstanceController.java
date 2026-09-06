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
    public ResponseEntity<SeatInstanceDto> createSeatInstance(@RequestBody SeatInstanceDto seatInstanceDto) {
        return new ResponseEntity<>(seatInstanceService.createSeatInstance(seatInstanceDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatInstanceDto> getSeatInstanceById(@PathVariable Long id) {
        return ResponseEntity.ok(seatInstanceService.getSeatInstanceById(id));
    }

    @GetMapping
    public ResponseEntity<List<SeatInstanceDto>> getAllSeatInstances() {
        return ResponseEntity.ok(seatInstanceService.getAllSeatInstances());
    }
}
