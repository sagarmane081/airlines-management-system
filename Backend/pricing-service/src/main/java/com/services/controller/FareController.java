package com.services.controller;

import com.common.dto.FareDto;
import com.services.service.FareService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fares")
public class FareController {

    private final FareService fareService;

    public FareController(FareService fareService) {
        this.fareService = fareService;
    }

    @PostMapping
    public ResponseEntity<FareDto> createFare(@RequestBody FareDto fareDto,
                                               @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(fareService.createFare(fareDto, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FareDto> getFareById(@PathVariable Long id) {
        return ResponseEntity.ok(fareService.getFareById(id));
    }

    @GetMapping
    public ResponseEntity<List<FareDto>> getAllFares() {
        return ResponseEntity.ok(fareService.getAllFares());
    }
}