package com.services.client;

import com.services.dto.FlightInstanceOwnerView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Deliberately no fallback - used to decide whether a write is authorized, not to enrich a
 * display field. Same reasoning as pricing-service's FlightClient.
 */
@FeignClient(name = "flight-ops-service")
public interface FlightClient {

    @GetMapping("/api/flight-instances/{id}")
    FlightInstanceOwnerView getFlightInstanceById(@PathVariable Long id);
}
