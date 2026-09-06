package com.services.client;

import com.common.dto.AirlineDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AirlineClientFallback implements AirlineClient {

    @Override
    public AirlineDto getAirlineById(Long id) {
        AirlineDto fallback = new AirlineDto();
        fallback.setId(id);
        fallback.setName("Unknown");
        return fallback;
    }

    @Override
    public List<AirlineDto> getAirlinesByIds(List<Long> ids) {
        return ids.stream().map(this::getAirlineById).toList();
    }
}