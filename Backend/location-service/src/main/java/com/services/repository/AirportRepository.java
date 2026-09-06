package com.services.repository;

import com.services.entity.Airport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AirportRepository extends JpaRepository<Airport, Long> {
    List<Airport> findAllByIdIn(List<Long> ids);
}
