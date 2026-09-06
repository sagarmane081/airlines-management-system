package com.services.client;

import com.common.dto.AirportDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "location-service", fallback = LocationClientFallback.class)
public interface LocationClient {

    @GetMapping("/api/airports/{id}")
    AirportDto getAirportById(@PathVariable Long id);

    @GetMapping("/api/airports")
    List<AirportDto> getAirportsByIds(@RequestParam("ids") List<Long> ids);
}
