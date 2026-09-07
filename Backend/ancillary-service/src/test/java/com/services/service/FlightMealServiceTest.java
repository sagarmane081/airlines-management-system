package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.FlightClient;
import com.services.dto.FlightMealDto;
import com.services.dto.FlightOwnerView;
import com.services.dto.MealDto;
import com.services.entity.FlightMeal;
import com.services.entity.Meal;
import com.services.entity.MealType;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.FlightMealRepository;
import com.services.repository.MealRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightMealServiceTest {

    @Mock
    private FlightMealRepository flightMealRepository;

    @Mock
    private MealRepository mealRepository;

    @Mock
    private FlightClient flightClient;

    @InjectMocks
    private FlightMealService flightMealService;

    private Meal mealOfAirline(Long airlineId) {
        return new Meal(1L, "Chicken Curry", MealType.NON_VEGETARIAN, "Spicy", airlineId);
    }

    private FlightOwnerView flightOwnedBy(Long airlineId, Long ownerId) {
        return new FlightOwnerView(5L, new AirlineDto(airlineId, "Air India", "AI", null, ownerId));
    }

    private MealDto mealRef(Long id) {
        MealDto dto = new MealDto();
        dto.setId(id);
        return dto;
    }

    private FlightMealDto request() {
        return new FlightMealDto(null, 5L, mealRef(1L), BigDecimal.valueOf(12), true);
    }

    @Test
    void createFlightMealSavesAndReturnsDtoForOwningAirlineOwner() {
        when(mealRepository.findById(1L)).thenReturn(Optional.of(mealOfAirline(10L)));
        when(flightClient.getFlightById(5L)).thenReturn(flightOwnedBy(10L, 42L));
        FlightMeal saved = new FlightMeal(1L, 5L, mealOfAirline(10L), BigDecimal.valueOf(12), true);
        when(flightMealRepository.save(any(FlightMeal.class))).thenReturn(saved);

        FlightMealDto result = flightMealService.createFlightMeal(request(), 42L, "ROLE_AIRLINE_OWNER");

        assertEquals(1L, result.getId());
        assertEquals(BigDecimal.valueOf(12), result.getPrice());
        assertTrue(result.getAvailable());
    }

    @Test
    void createFlightMealThrowsForbiddenForCustomer() {
        assertThrows(ForbiddenException.class, () -> flightMealService.createFlightMeal(request(), 1L, "ROLE_CUSTOMER"));
        verify(flightMealRepository, never()).save(any());
        verifyNoInteractions(mealRepository, flightClient);
    }

    @Test
    void createFlightMealThrowsForbiddenForNonOwningAirlineOwner() {
        when(mealRepository.findById(1L)).thenReturn(Optional.of(mealOfAirline(10L)));
        when(flightClient.getFlightById(5L)).thenReturn(flightOwnedBy(10L, 42L));

        assertThrows(ForbiddenException.class, () -> flightMealService.createFlightMeal(request(), 999L, "ROLE_AIRLINE_OWNER"));
        verify(flightMealRepository, never()).save(any());
    }

    @Test
    void createFlightMealSucceedsForSystemAdminEvenWhenNotOwner() {
        when(mealRepository.findById(1L)).thenReturn(Optional.of(mealOfAirline(10L)));
        when(flightClient.getFlightById(5L)).thenReturn(flightOwnedBy(10L, 42L));
        FlightMeal saved = new FlightMeal(1L, 5L, mealOfAirline(10L), BigDecimal.valueOf(12), true);
        when(flightMealRepository.save(any(FlightMeal.class))).thenReturn(saved);

        FlightMealDto result = flightMealService.createFlightMeal(request(), 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void createFlightMealThrowsWhenMealBelongsToDifferentAirlineEvenForAdmin() {
        when(mealRepository.findById(1L)).thenReturn(Optional.of(mealOfAirline(20L)));
        when(flightClient.getFlightById(5L)).thenReturn(flightOwnedBy(10L, 42L));

        assertThrows(ForbiddenException.class, () -> flightMealService.createFlightMeal(request(), 999L, "ROLE_SYSTEM_ADMIN"));
        verify(flightMealRepository, never()).save(any());
    }

    @Test
    void createFlightMealThrowsWhenMealMissing() {
        when(mealRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightMealService.createFlightMeal(request(), 42L, "ROLE_AIRLINE_OWNER"));
        verify(flightMealRepository, never()).save(any());
        verifyNoInteractions(flightClient);
    }

    @Test
    void getFlightMealByIdThrowsWhenMissing() {
        when(flightMealRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightMealService.getFlightMealById(99L));
    }

    @Test
    void getAllFlightMealsMapsEveryRow() {
        when(flightMealRepository.findAll()).thenReturn(List.of(
                new FlightMeal(1L, 5L, mealOfAirline(10L), BigDecimal.valueOf(12), true),
                new FlightMeal(2L, 6L, mealOfAirline(10L), BigDecimal.valueOf(15), false)));

        List<FlightMealDto> result = flightMealService.getAllFlightMeals();

        assertEquals(2, result.size());
        assertFalse(result.get(1).getAvailable());
    }
}
