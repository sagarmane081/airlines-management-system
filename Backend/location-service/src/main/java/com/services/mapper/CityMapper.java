package com.services.mapper;

import com.common.dto.CityDto;
import com.services.entity.City;

public class CityMapper {

    public static CityDto toDto(City city) {
        CityDto dto = new CityDto();
        dto.setId(city.getId());
        dto.setName(city.getName());
        dto.setCountry(city.getCountry());
        dto.setTimezone(city.getTimezone());
        return dto;
    }

    public static City toEntity(CityDto dto) {
        City city = new City();
        city.setId(dto.getId());
        city.setName(dto.getName());
        city.setCountry(dto.getCountry());
        city.setTimezone(dto.getTimezone());
        return city;
    }
}