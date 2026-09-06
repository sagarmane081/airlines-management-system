package com.services.client;

import com.common.dto.CityDto;
import org.springframework.stereotype.Component;

@Component
public class LocationClientFallback implements LocationClient {

    @Override
    public CityDto getCityById(Long id) {
        CityDto fallback = new CityDto();
        fallback.setId(id);
        fallback.setName("Unknown");
        return fallback;
    }
}