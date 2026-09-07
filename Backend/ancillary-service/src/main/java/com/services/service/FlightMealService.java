package com.services.service;

import com.services.client.FlightClient;
import com.services.dto.FlightMealDto;
import com.services.dto.FlightOwnerView;
import com.services.entity.FlightMeal;
import com.services.entity.Meal;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.FlightMealMapper;
import com.services.repository.FlightMealRepository;
import com.services.repository.MealRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightMealService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final FlightMealRepository flightMealRepository;
    private final MealRepository mealRepository;
    private final FlightClient flightClient;

    public FlightMealService(FlightMealRepository flightMealRepository, MealRepository mealRepository,
                              FlightClient flightClient) {
        this.flightMealRepository = flightMealRepository;
        this.mealRepository = mealRepository;
        this.flightClient = flightClient;
    }

    public FlightMealDto createFlightMeal(FlightMealDto dto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create flight meals");
        }
        Long mealId = dto.getMeal() != null ? dto.getMeal().getId() : null;
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal not found with id: " + mealId));

        FlightOwnerView flight = flightClient.getFlightById(dto.getFlightId());
        AirlineOwnershipChecker.requireAirlineOwnership(flight.getAirline(), requesterId, requesterRole);
        requireSameAirline(flight, meal);

        FlightMeal saved = flightMealRepository.save(FlightMealMapper.toEntity(dto, meal));
        return FlightMealMapper.toDto(saved);
    }

    /**
     * Same data-consistency rule as FlightAncillaryService: a flight's airline can only attach
     * its own meal catalog, applies unconditionally (even to admins), since it's about not
     * cross-wiring two airlines' catalogs rather than about who owns what.
     */
    private void requireSameAirline(FlightOwnerView flight, Meal meal) {
        if (!flight.getAirline().getId().equals(meal.getAirlineId())) {
            throw new ForbiddenException(
                    "Meal " + meal.getId() + " belongs to a different airline than flight " + flight.getId());
        }
    }

    public FlightMealDto getFlightMealById(Long id) {
        FlightMeal flightMeal = flightMealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightMeal not found with id: " + id));
        return FlightMealMapper.toDto(flightMeal);
    }

    public List<FlightMealDto> getAllFlightMeals() {
        return flightMealRepository.findAll().stream().map(FlightMealMapper::toDto).toList();
    }
}
