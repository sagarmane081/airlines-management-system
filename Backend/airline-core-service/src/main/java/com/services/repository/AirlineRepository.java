package com.services.repository;

import com.services.entity.Airline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AirlineRepository extends JpaRepository<Airline, Long> {
    List<Airline> findAllByIdIn(List<Long> ids);
}