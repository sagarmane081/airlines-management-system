package com.services.mapper;

import com.services.dto.FlightDto;
import com.services.dto.FlightScheduleDto;
import com.services.entity.Flight;
import com.services.entity.FlightSchedule;

public class FlightScheduleMapper {

    public static FlightScheduleDto toDto(FlightSchedule schedule, FlightDto flightDto) {
        FlightScheduleDto dto = new FlightScheduleDto();
        dto.setId(schedule.getId());
        dto.setFlight(flightDto);
        dto.setDepartureTime(schedule.getDepartureTime());
        dto.setArrivalTime(schedule.getArrivalTime());
        dto.setStartDate(schedule.getStartDate());
        dto.setEndDate(schedule.getEndDate());
        dto.setOperatingDays(schedule.getOperatingDays());
        return dto;
    }

    public static FlightSchedule toEntity(FlightScheduleDto dto, Flight flight) {
        FlightSchedule schedule = new FlightSchedule();
        schedule.setFlight(flight);
        schedule.setDepartureTime(dto.getDepartureTime());
        schedule.setArrivalTime(dto.getArrivalTime());
        schedule.setStartDate(dto.getStartDate());
        schedule.setEndDate(dto.getEndDate());
        schedule.setOperatingDays(dto.getOperatingDays());
        return schedule;
    }
}
