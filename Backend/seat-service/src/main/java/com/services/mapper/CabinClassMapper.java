package com.services.mapper;

import com.services.dto.CabinClassDto;
import com.services.entity.CabinClass;
import com.services.entity.SeatMap;

public class CabinClassMapper {

    public static CabinClassDto toDto(CabinClass cabinClass) {
        CabinClassDto dto = new CabinClassDto();
        dto.setId(cabinClass.getId());
        dto.setName(cabinClass.getName());
        dto.setStartRow(cabinClass.getStartRow());
        dto.setEndRow(cabinClass.getEndRow());
        dto.setSeatsPerRow(cabinClass.getSeatsPerRow());
        dto.setSeatPitchInches(cabinClass.getSeatPitchInches());
        dto.setSeatMap(SeatMapMapper.toDto(cabinClass.getSeatMap()));
        return dto;
    }

    public static CabinClass toEntity(CabinClassDto dto, SeatMap seatMap) {
        CabinClass cabinClass = new CabinClass();
        cabinClass.setName(dto.getName());
        cabinClass.setStartRow(dto.getStartRow());
        cabinClass.setEndRow(dto.getEndRow());
        cabinClass.setSeatsPerRow(dto.getSeatsPerRow());
        cabinClass.setSeatPitchInches(dto.getSeatPitchInches());
        cabinClass.setSeatMap(seatMap);
        return cabinClass;
    }
}
