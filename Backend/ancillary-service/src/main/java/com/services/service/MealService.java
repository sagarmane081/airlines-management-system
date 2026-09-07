package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.AirlineClient;
import com.services.dto.MealDto;
import com.services.entity.Meal;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.MealMapper;
import com.services.repository.MealRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MealService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final MealRepository mealRepository;
    private final AirlineClient airlineClient;

    public MealService(MealRepository mealRepository, AirlineClient airlineClient) {
        this.mealRepository = mealRepository;
        this.airlineClient = airlineClient;
    }

    public MealDto createMeal(MealDto mealDto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create meals");
        }
        AirlineDto airline = airlineClient.getAirlineById(mealDto.getAirlineId());
        AirlineOwnershipChecker.requireAirlineOwnership(airline, requesterId, requesterRole);

        Meal saved = mealRepository.save(MealMapper.toEntity(mealDto));
        return MealMapper.toDto(saved);
    }

    public MealDto getMealById(Long id) {
        Meal meal = mealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meal not found with id: " + id));
        return MealMapper.toDto(meal);
    }

    public List<MealDto> getAllMeals() {
        return mealRepository.findAll().stream().map(MealMapper::toDto).toList();
    }
}
