package com.services.repository;

import com.services.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Long> {
}
