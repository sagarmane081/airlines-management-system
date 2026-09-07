package com.services.mapper;

import com.services.dto.MealDto;
import com.services.entity.Meal;

public class MealMapper {

    public static MealDto toDto(Meal meal) {
        MealDto dto = new MealDto();
        dto.setId(meal.getId());
        dto.setName(meal.getName());
        dto.setMealType(meal.getMealType());
        dto.setDescription(meal.getDescription());
        dto.setAirlineId(meal.getAirlineId());
        return dto;
    }

    public static Meal toEntity(MealDto dto) {
        Meal meal = new Meal();
        meal.setName(dto.getName());
        meal.setMealType(dto.getMealType());
        meal.setDescription(dto.getDescription());
        meal.setAirlineId(dto.getAirlineId());
        return meal;
    }
}
