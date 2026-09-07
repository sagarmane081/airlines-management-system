package com.services.controller;

import com.services.dto.CabinClassDto;
import com.services.service.CabinClassService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cabin-classes")
public class CabinClassController {

    private final CabinClassService cabinClassService;

    public CabinClassController(CabinClassService cabinClassService) {
        this.cabinClassService = cabinClassService;
    }

    @PostMapping
    public ResponseEntity<CabinClassDto> createCabinClass(@RequestBody CabinClassDto cabinClassDto,
                                                           @RequestHeader("X-User-Id") Long requesterId,
                                                           @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(cabinClassService.createCabinClass(cabinClassDto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CabinClassDto> getCabinClassById(@PathVariable Long id) {
        return ResponseEntity.ok(cabinClassService.getCabinClassById(id));
    }

    @GetMapping
    public ResponseEntity<List<CabinClassDto>> getAllCabinClasses() {
        return ResponseEntity.ok(cabinClassService.getAllCabinClasses());
    }
}
