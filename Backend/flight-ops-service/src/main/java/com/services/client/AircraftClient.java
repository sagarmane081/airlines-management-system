package com.services.client;

import com.common.dto.AircraftDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "airline-core-service", contextId = "aircraftClient", fallback = AircraftClientFallback.class)
public interface AircraftClient {

    @GetMapping("/api/aircrafts/{id}")
    AircraftDto getAircraftById(@PathVariable Long id);

    @GetMapping("/api/aircrafts")
    List<AircraftDto> getAircraftByIds(@RequestParam("ids") List<Long> ids);
}
