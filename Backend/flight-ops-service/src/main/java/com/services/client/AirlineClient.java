package com.services.client;

import com.common.dto.AirlineDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "airline-core-service", fallback = AirlineClientFallback.class)
public interface AirlineClient {

    @GetMapping("/api/airlines/{id}")
    AirlineDto getAirlineById(@PathVariable Long id);

    @GetMapping("/api/airlines")
    List<AirlineDto> getAirlinesByIds(@RequestParam("ids") List<Long> ids);
}