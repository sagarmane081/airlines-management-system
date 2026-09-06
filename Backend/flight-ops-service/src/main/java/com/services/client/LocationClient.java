package com.services.client;

import com.common.dto.CityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "location-service",  fallback = LocationClientFallback.class)
public interface LocationClient {

    @GetMapping("/api/cities/{id}")
    CityDto getCityById(@PathVariable Long id);

    @GetMapping("/api/cities")
    List<CityDto> getCitiesByIds(@RequestParam("ids") List<Long> ids);
}