package com.services.mapper;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.services.entity.Aircraft;
import com.services.entity.AircraftStatus;
import com.services.entity.Airline;

public class AircraftMapper {

    public static Aircraft toEntity(AircraftDto dto, Airline airline) {
        Aircraft aircraft = new Aircraft();
        aircraft.setId(dto.getId());
        aircraft.setRegistrationNumber(dto.getRegistrationNumber());
        aircraft.setModel(dto.getModel());
        aircraft.setManufacturer(dto.getManufacturer());
        aircraft.setTotalSeats(dto.getTotalSeats());
        aircraft.setStatus(dto.getStatus() != null ? AircraftStatus.valueOf(dto.getStatus()) : null);
        aircraft.setAirline(airline);
        return aircraft;
    }

    public static AircraftDto toDto(Aircraft aircraft, AirlineDto airline) {
        AircraftDto dto = new AircraftDto();
        dto.setId(aircraft.getId());
        dto.setRegistrationNumber(aircraft.getRegistrationNumber());
        dto.setModel(aircraft.getModel());
        dto.setManufacturer(aircraft.getManufacturer());
        dto.setTotalSeats(aircraft.getTotalSeats());
        dto.setStatus(aircraft.getStatus() != null ? aircraft.getStatus().name() : null);
        dto.setAirline(airline);
        return dto;
    }
}
