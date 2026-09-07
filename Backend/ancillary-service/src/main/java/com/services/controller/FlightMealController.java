package com.services.controller;

import com.services.dto.FlightMealDto;
import com.services.service.FlightMealService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flight-meals")
public class FlightMealController {

    private final FlightMealService flightMealService;

    public FlightMealController(FlightMealService flightMealService) {
        this.flightMealService = flightMealService;
    }

    @PostMapping
    public ResponseEntity<FlightMealDto> createFlightMeal(@RequestBody FlightMealDto dto,
                                                            @RequestHeader("X-User-Id") Long requesterId,
                                                            @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(flightMealService.createFlightMeal(dto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightMealDto> getFlightMealById(@PathVariable Long id) {
        return ResponseEntity.ok(flightMealService.getFlightMealById(id));
    }

    @GetMapping
    public ResponseEntity<List<FlightMealDto>> getAllFlightMeals() {
        return ResponseEntity.ok(flightMealService.getAllFlightMeals());
    }
}
