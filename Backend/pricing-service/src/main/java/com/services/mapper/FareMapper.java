package com.services.mapper;

import com.services.dto.FareDto;
import com.services.entity.Fare;

public class FareMapper {

    public static FareDto toDto(Fare fare) {
        FareDto dto = new FareDto();
        dto.setId(fare.getId());
        dto.setFlightId(fare.getFlightId());
        dto.setCabinClass(fare.getCabinClass());
        dto.setPrice(fare.getPrice());
        dto.setCurrency(fare.getCurrency());
        return dto;
    }

    public static Fare toEntity(FareDto dto) {
        Fare fare = new Fare();
        fare.setId(dto.getId());
        fare.setFlightId(dto.getFlightId());
        fare.setCabinClass(dto.getCabinClass());
        fare.setPrice(dto.getPrice());
        fare.setCurrency(dto.getCurrency());
        return fare;
    }
}