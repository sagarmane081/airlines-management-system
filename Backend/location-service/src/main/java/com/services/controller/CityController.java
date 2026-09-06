package com.services.controller;

import com.common.dto.CityDto;
import com.services.service.CityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @PostMapping
    public ResponseEntity<CityDto> createCity(@RequestBody CityDto cityDto,
                                               @RequestHeader("X-User-Roles") String role) {
        CityDto created = cityService.createCity(cityDto, role);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CityDto> getCityById(@PathVariable Long id) {
        CityDto city = cityService.getCityById(id);
        return ResponseEntity.ok(city);
    }

    @GetMapping
    public ResponseEntity<List<CityDto>> getAllCities(@RequestParam(required = false) List<Long> ids) {
        List<CityDto> cities = (ids == null || ids.isEmpty())
                ? cityService.getAllCities()
                : cityService.getCitiesByIds(ids);
        return ResponseEntity.ok(cities);
    }
}