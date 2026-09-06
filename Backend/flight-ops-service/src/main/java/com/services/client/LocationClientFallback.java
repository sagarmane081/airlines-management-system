package com.services.client;

import com.common.dto.AirportDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LocationClientFallback implements LocationClient {

    @Override
    public AirportDto getAirportById(Long id) {
        AirportDto fallback = new AirportDto();
        fallback.setId(id);
        fallback.setName("Unknown");
        return fallback;
    }

    @Override
    public List<AirportDto> getAirportsByIds(List<Long> ids) {
        return ids.stream().map(this::getAirportById).toList();
    }
}
