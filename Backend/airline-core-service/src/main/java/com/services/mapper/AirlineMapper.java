package com.services.mapper;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import com.services.entity.Airline;

public class AirlineMapper {

    public static AirlineDto toDto(Airline airline, CityDto headquartersCity) {
        AirlineDto dto = new AirlineDto();
        dto.setId(airline.getId());
        dto.setName(airline.getName());
        dto.setIataCode(airline.getIataCode());
        dto.setHeadquartersCity(headquartersCity);
        return dto;
    }

    public static Airline toEntity(AirlineDto dto) {
        Airline airline = new Airline();
        airline.setId(dto.getId());
        airline.setName(dto.getName());
        airline.setIataCode(dto.getIataCode());
        if (dto.getHeadquartersCity() != null) {
            airline.setHeadquartersCityId(dto.getHeadquartersCity().getId());
        }
        return airline;
    }
}