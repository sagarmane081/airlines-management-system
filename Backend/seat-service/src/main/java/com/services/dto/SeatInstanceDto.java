package com.services.dto;

import com.services.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatInstanceDto {
    private Long id;
    private Long flightInstanceId;
    private String seatNumber;
    private String cabinClass;
    private SeatStatus status;
}
