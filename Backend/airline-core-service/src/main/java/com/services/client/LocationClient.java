package com.services.client;

import com.common.dto.CityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "location-service", fallback = LocationClientFallback.class)
public interface LocationClient {

    @GetMapping("/api/cities/{id}")
    CityDto getCityById(@PathVariable Long id);
}
