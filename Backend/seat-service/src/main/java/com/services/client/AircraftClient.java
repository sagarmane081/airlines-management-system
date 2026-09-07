package com.services.client;

import com.common.dto.AircraftDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Deliberately no fallback - used to decide whether a write is authorized, not to enrich a
 * display field. Same reasoning as this service's own FlightClient.
 */
@FeignClient(name = "airline-core-service")
public interface AircraftClient {

    @GetMapping("/api/aircrafts/{id}")
    AircraftDto getAircraftById(@PathVariable Long id);
}
