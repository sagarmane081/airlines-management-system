package com.services.repository;

import com.services.entity.FlightInstance;
import com.services.entity.FlightInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FlightInstanceRepository extends JpaRepository<FlightInstance, Long> {

    boolean existsByFlightScheduleIdAndDepartureTime(Long flightScheduleId, LocalDateTime departureTime);

    List<FlightInstance> findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus(
            Long departureAirportId, Long arrivalAirportId, LocalDateTime from, LocalDateTime to, FlightInstanceStatus status);
}
