package com.services.mapper;

import com.common.dto.AirportDto;
import com.services.entity.Airport;
import com.services.entity.City;

public class AirportMapper {

    public static Airport toEntity(AirportDto dto, City city) {
        Airport airport = new Airport();
        airport.setId(dto.getId());
        airport.setIataCode(dto.getIataCode());
        airport.setName(dto.getName());
        airport.setCity(city);
        return airport;
    }

    public static AirportDto toDto(Airport airport) {
        AirportDto dto = new AirportDto();
        dto.setId(airport.getId());
        dto.setIataCode(airport.getIataCode());
        dto.setName(airport.getName());
        dto.setCity(CityMapper.toDto(airport.getCity()));
        return dto;
    }
}
