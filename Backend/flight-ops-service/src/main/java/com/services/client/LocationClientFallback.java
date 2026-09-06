package com.services.client;

import com.common.dto.CityDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LocationClientFallback implements LocationClient {

    @Override
    public CityDto getCityById(Long id) {
        CityDto fallback = new CityDto();
        fallback.setId(id);
        fallback.setName("Unknown");
        return fallback;
    }

    @Override
    public List<CityDto> getCitiesByIds(List<Long> ids) {
        return ids.stream().map(this::getCityById).toList();
    }
}