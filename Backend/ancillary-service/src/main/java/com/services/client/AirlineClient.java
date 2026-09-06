package com.services.client;

import com.common.dto.AirlineDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Deliberately no fallback - used to decide whether a write is authorized, not to enrich a
 * display field. Same reasoning as pricing-service/seat-service's FlightClient.
 */
@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/{id}")
    AirlineDto getAirlineById(@PathVariable Long id);
}
