package com.services.client;

import com.services.dto.FlightOwnerView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Deliberately no fallback - used to decide whether a write is authorized, not to enrich a
 * display field. Same reasoning as this service's own AirlineClient.
 */
@FeignClient(name = "flight-ops-service")
public interface FlightClient {

    @GetMapping("/api/flights/{id}")
    FlightOwnerView getFlightById(@PathVariable Long id);
}
