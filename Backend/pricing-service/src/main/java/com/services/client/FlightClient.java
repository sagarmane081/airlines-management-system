package com.services.client;

import com.services.dto.FlightOwnerView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Deliberately no fallback - this is used to decide whether a write is authorized, not to enrich
 * a display field. A fake "here's some airline" fallback would either silently let an unauthorized
 * write through or silently block a legitimate owner, so failing loudly (503 via
 * NoFallbackAvailableException) when flight-ops-service is unreachable is the correct behavior,
 * same reasoning as booking-service's PricingClient/PaymentClient.
 */
@FeignClient(name = "flight-ops-service")
public interface FlightClient {

    @GetMapping("/api/flights/{id}")
    FlightOwnerView getFlightById(@PathVariable Long id);
}
