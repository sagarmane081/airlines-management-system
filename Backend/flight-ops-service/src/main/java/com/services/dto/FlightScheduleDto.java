package com.services.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightScheduleDto {
    private Long id;
    private FlightDto flight;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private LocalDate startDate;
    private LocalDate endDate;
    private Set<DayOfWeek> operatingDays = new HashSet<>();
}
