package com.services.dto;

import com.services.entity.CabinClassType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CabinClassDto {
    private Long id;
    private CabinClassType name;
    private Integer startRow;
    private Integer endRow;
    private Integer seatsPerRow;
    private Integer seatPitchInches;
    private SeatMapDto seatMap;
}
