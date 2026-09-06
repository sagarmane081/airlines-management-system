package com.services.client;

import com.common.dto.AirlineDto;
import org.springframework.stereotype.Component;

@Component
public class AirlineClientFallback implements AirlineClient {

    @Override
    public AirlineDto getAirlineById(Long id) {
        AirlineDto fallback = new AirlineDto();
        fallback.setId(id);
        fallback.setName("Unknown");
        return fallback;
    }
}