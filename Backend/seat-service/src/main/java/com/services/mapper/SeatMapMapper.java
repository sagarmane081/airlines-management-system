package com.services.mapper;

import com.services.dto.SeatMapDto;
import com.services.entity.SeatMap;

public class SeatMapMapper {

    public static SeatMapDto toDto(SeatMap seatMap) {
        SeatMapDto dto = new SeatMapDto();
        dto.setId(seatMap.getId());
        dto.setAircraftId(seatMap.getAircraftId());
        dto.setTotalRows(seatMap.getTotalRows());
        return dto;
    }

    public static SeatMap toEntity(SeatMapDto dto) {
        SeatMap seatMap = new SeatMap();
        seatMap.setId(dto.getId());
        seatMap.setAircraftId(dto.getAircraftId());
        seatMap.setTotalRows(dto.getTotalRows());
        return seatMap;
    }
}
