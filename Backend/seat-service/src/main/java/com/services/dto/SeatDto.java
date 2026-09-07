package com.services.dto;

import com.services.entity.SeatType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatDto {
    private Long id;
    private Integer seatRow;
    private String columnLetter;
    private SeatType seatType;
    // Boolean, not boolean - see FareRulesDto for why a primitive here breaks partial requests.
    private Boolean exitRow;
    private String seatNumber;
    private CabinClassDto cabinClass;
}
