package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.AirlineClient;
import com.services.dto.MealDto;
import com.services.entity.Meal;
import com.services.entity.MealType;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.MealRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock
    private MealRepository mealRepository;

    @Mock
    private AirlineClient airlineClient;

    @InjectMocks
    private MealService mealService;

    private AirlineDto airlineOwnedBy(Long ownerId) {
        return new AirlineDto(10L, "Air India", "AI", null, ownerId);
    }

    @Test
    void createMealSavesAndReturnsDtoForOwningAirlineOwner() {
        MealDto request = new MealDto(null, "Chicken Curry", MealType.NON_VEGETARIAN, "Spicy", 10L);
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        Meal saved = new Meal(1L, "Chicken Curry", MealType.NON_VEGETARIAN, "Spicy", 10L);
        when(mealRepository.save(any(Meal.class))).thenReturn(saved);

        MealDto result = mealService.createMeal(request, 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(MealType.NON_VEGETARIAN, result.getMealType());
    }

    @Test
    void createMealThrowsForbiddenForCustomer() {
        MealDto request = new MealDto(null, "Chicken Curry", MealType.NON_VEGETARIAN, "Spicy", 10L);

        assertThrows(ForbiddenException.class, () -> mealService.createMeal(request, 1L, "ROLE_CUSTOMER"));
        verify(mealRepository, never()).save(any());
        verifyNoInteractions(airlineClient);
    }

    @Test
    void createMealThrowsForbiddenForNonOwningAirlineOwner() {
        MealDto request = new MealDto(null, "Chicken Curry", MealType.NON_VEGETARIAN, "Spicy", 10L);
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));

        assertThrows(ForbiddenException.class, () -> mealService.createMeal(request, 999L, "ROLE_AIRLINE_OWNER"));
        verify(mealRepository, never()).save(any());
    }

    @Test
    void createMealSucceedsForSystemAdminEvenWhenNotOwner() {
        MealDto request = new MealDto(null, "Chicken Curry", MealType.NON_VEGETARIAN, "Spicy", 10L);
        when(airlineClient.getAirlineById(10L)).thenReturn(airlineOwnedBy(42L));
        Meal saved = new Meal(1L, "Chicken Curry", MealType.NON_VEGETARIAN, "Spicy", 10L);
        when(mealRepository.save(any(Meal.class))).thenReturn(saved);

        MealDto result = mealService.createMeal(request, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void getMealByIdThrowsWhenMissing() {
        when(mealRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> mealService.getMealById(99L));
    }

    @Test
    void getAllMealsMapsEveryRow() {
        when(mealRepository.findAll()).thenReturn(List.of(
                new Meal(1L, "Chicken Curry", MealType.NON_VEGETARIAN, "Spicy", 10L),
                new Meal(2L, "Veg Biryani", MealType.VEGETARIAN, "Mild", 10L)));

        List<MealDto> result = mealService.getAllMeals();

        assertEquals(2, result.size());
        assertEquals(MealType.VEGETARIAN, result.get(1).getMealType());
    }
}
