package com.services.repository;

import com.services.entity.Fare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FareRepository extends JpaRepository<Fare, Long> {
    List<Fare> findAllByFlightIdIn(List<Long> flightIds);
}