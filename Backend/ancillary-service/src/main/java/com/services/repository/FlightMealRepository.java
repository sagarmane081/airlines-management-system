package com.services.repository;

import com.services.entity.FlightMeal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightMealRepository extends JpaRepository<FlightMeal, Long> {
}
