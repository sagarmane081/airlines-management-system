package com.services.mapper;

import com.services.dto.SeatInstanceDto;
import com.services.entity.SeatInstance;

public class SeatInstanceMapper {

    public static SeatInstanceDto toDto(SeatInstance seatInstance) {
        SeatInstanceDto dto = new SeatInstanceDto();
        dto.setId(seatInstance.getId());
        dto.setFlightInstanceId(seatInstance.getFlightInstanceId());
        dto.setSeatNumber(seatInstance.getSeatNumber());
        dto.setCabinClass(seatInstance.getCabinClass());
        dto.setStatus(seatInstance.getStatus());
        return dto;
    }

    public static SeatInstance toEntity(SeatInstanceDto dto) {
        SeatInstance seatInstance = new SeatInstance();
        seatInstance.setId(dto.getId());
        seatInstance.setFlightInstanceId(dto.getFlightInstanceId());
        seatInstance.setSeatNumber(dto.getSeatNumber());
        seatInstance.setCabinClass(dto.getCabinClass());
        seatInstance.setStatus(dto.getStatus());
        return seatInstance;
    }
}
