package com.services.controller;

import com.services.dto.MealDto;
import com.services.service.MealService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    @PostMapping
    public ResponseEntity<MealDto> createMeal(@RequestBody MealDto mealDto,
                                               @RequestHeader("X-User-Id") Long requesterId,
                                               @RequestHeader("X-User-Roles") String role) {
        return new ResponseEntity<>(mealService.createMeal(mealDto, requesterId, role), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MealDto> getMealById(@PathVariable Long id) {
        return ResponseEntity.ok(mealService.getMealById(id));
    }

    @GetMapping
    public ResponseEntity<List<MealDto>> getAllMeals() {
        return ResponseEntity.ok(mealService.getAllMeals());
    }
}
