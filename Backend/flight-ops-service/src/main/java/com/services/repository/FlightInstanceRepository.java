package com.services.repository;

import com.services.entity.FlightInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface FlightInstanceRepository extends JpaRepository<FlightInstance, Long> {

    boolean existsByFlightScheduleIdAndDepartureTime(Long flightScheduleId, LocalDateTime departureTime);
}
