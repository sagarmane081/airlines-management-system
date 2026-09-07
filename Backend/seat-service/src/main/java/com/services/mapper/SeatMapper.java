package com.services.mapper;

import com.services.dto.SeatDto;
import com.services.entity.CabinClass;
import com.services.entity.Seat;

public class SeatMapper {

    public static SeatDto toDto(Seat seat) {
        SeatDto dto = new SeatDto();
        dto.setId(seat.getId());
        dto.setSeatRow(seat.getSeatRow());
        dto.setColumnLetter(seat.getColumnLetter());
        dto.setSeatType(seat.getSeatType());
        dto.setExitRow(seat.isExitRow());
        dto.setSeatNumber(seat.getSeatNumber());
        dto.setCabinClass(CabinClassMapper.toDto(seat.getCabinClass()));
        return dto;
    }

    public static Seat toEntity(SeatDto dto, CabinClass cabinClass) {
        Seat seat = new Seat();
        seat.setSeatRow(dto.getSeatRow());
        seat.setColumnLetter(dto.getColumnLetter());
        seat.setSeatType(dto.getSeatType());
        seat.setExitRow(Boolean.TRUE.equals(dto.getExitRow()));
        seat.setCabinClass(cabinClass);
        return seat;
    }
}
