package com.services.dto;

import com.services.entity.MealType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealDto {
    private Long id;
    private String name;
    private MealType mealType;
    private String description;
    private Long airlineId;
}
