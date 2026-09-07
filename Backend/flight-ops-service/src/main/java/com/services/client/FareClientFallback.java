package com.services.client;

import com.common.dto.FareDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FareClientFallback implements FareClient {

    @Override
    public List<FareDto> getFaresByFlightIds(List<Long> flightIds) {
        return List.of();
    }
}
