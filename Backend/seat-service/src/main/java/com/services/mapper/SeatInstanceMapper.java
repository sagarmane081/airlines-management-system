package com.services.mapper;

import com.services.dto.SeatInstanceDto;
import com.services.entity.Seat;
import com.services.entity.SeatInstance;

public class SeatInstanceMapper {

    public static SeatInstanceDto toDto(SeatInstance seatInstance) {
        SeatInstanceDto dto = new SeatInstanceDto();
        dto.setId(seatInstance.getId());
        dto.setFlightInstanceId(seatInstance.getFlightInstanceId());
        dto.setSeat(seatInstance.getSeat() != null ? SeatMapper.toDto(seatInstance.getSeat()) : null);
        dto.setStatus(seatInstance.getStatus());
        return dto;
    }

    public static SeatInstance toEntity(SeatInstanceDto dto, Seat seat) {
        SeatInstance seatInstance = new SeatInstance();
        seatInstance.setId(dto.getId());
        seatInstance.setFlightInstanceId(dto.getFlightInstanceId());
        seatInstance.setSeat(seat);
        seatInstance.setStatus(dto.getStatus());
        return seatInstance;
    }
}
