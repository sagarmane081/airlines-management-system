package com.services.client;

import com.common.dto.AircraftDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AircraftClientFallback implements AircraftClient {

    @Override
    public AircraftDto getAircraftById(Long id) {
        AircraftDto fallback = new AircraftDto();
        fallback.setId(id);
        fallback.setModel("Unknown");
        return fallback;
    }

    @Override
    public List<AircraftDto> getAircraftByIds(List<Long> ids) {
        return ids.stream().map(this::getAircraftById).toList();
    }
}
