package com.services.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapDto {
    private Long id;
    private Long aircraftId;
    private Integer totalRows;
}
