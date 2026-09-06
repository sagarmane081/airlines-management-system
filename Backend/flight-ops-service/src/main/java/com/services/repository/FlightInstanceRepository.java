package com.services.repository;

import com.services.entity.FlightInstance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightInstanceRepository extends JpaRepository<FlightInstance, Long> {
}