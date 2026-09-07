package com.services.client;

import com.common.dto.FareDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Unlike this project's other Feign clients used for authorization checks (deliberately no
 * fallback), this one backs a browse/search path - a degraded "no prices shown" search result is
 * far better UX than a failed search, so a fallback is the right call here, same reasoning as the
 * enrichment fallbacks (AirlineClientFallback, LocationClientFallback) rather than the
 * money-critical-call ones (booking-service's PricingClient/PaymentClient).
 */
@FeignClient(name = "pricing-service", fallback = FareClientFallback.class)
public interface FareClient {

    @GetMapping("/api/fares")
    List<FareDto> getFaresByFlightIds(@RequestParam("flightIds") List<Long> flightIds);
}
