package com.services.mapper;

import com.services.dto.FlightMealDto;
import com.services.entity.FlightMeal;
import com.services.entity.Meal;

public class FlightMealMapper {

    public static FlightMealDto toDto(FlightMeal flightMeal) {
        FlightMealDto dto = new FlightMealDto();
        dto.setId(flightMeal.getId());
        dto.setFlightId(flightMeal.getFlightId());
        dto.setMeal(MealMapper.toDto(flightMeal.getMeal()));
        dto.setPrice(flightMeal.getPrice());
        dto.setAvailable(flightMeal.isAvailable());
        return dto;
    }

    public static FlightMeal toEntity(FlightMealDto dto, Meal meal) {
        FlightMeal flightMeal = new FlightMeal();
        flightMeal.setFlightId(dto.getFlightId());
        flightMeal.setMeal(meal);
        flightMeal.setPrice(dto.getPrice());
        flightMeal.setAvailable(Boolean.TRUE.equals(dto.getAvailable()));
        return flightMeal;
    }
}
