package com.services.client;

import com.common.dto.FareDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "pricing-service")
public interface PricingClient {

    @GetMapping("/api/fares/{id}")
    FareDto getFareById(@PathVariable Long id);
}
