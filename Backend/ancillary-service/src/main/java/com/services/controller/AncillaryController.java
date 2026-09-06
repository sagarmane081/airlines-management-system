package com.services.controller;

import com.services.dto.AncillaryDto;
import com.services.service.AncillaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ancillaries")
public class AncillaryController {

    private final AncillaryService ancillaryService;

    public AncillaryController(AncillaryService ancillaryService) {
        this.ancillaryService = ancillaryService;
    }

    @PostMapping
    public ResponseEntity<AncillaryDto> createAncillary(@RequestBody AncillaryDto ancillaryDto) {
        return new ResponseEntity<>(ancillaryService.createAncillary(ancillaryDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AncillaryDto> getAncillaryById(@PathVariable Long id) {
        return ResponseEntity.ok(ancillaryService.getAncillaryById(id));
    }

    @GetMapping
    public ResponseEntity<List<AncillaryDto>> getAllAncillaries() {
        return ResponseEntity.ok(ancillaryService.getAllAncillaries());
    }
}
