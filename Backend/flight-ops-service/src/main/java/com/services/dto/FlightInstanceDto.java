package com.services.dto;

import com.common.dto.AircraftDto;
import com.services.entity.FlightInstanceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightInstanceDto {
    private Long id;
    private FlightDto flight;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private FlightInstanceStatus status;
    private AircraftDto aircraft;
    private Long flightScheduleId;
}
