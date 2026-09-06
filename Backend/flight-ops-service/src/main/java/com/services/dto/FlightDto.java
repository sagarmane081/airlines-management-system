package com.services.dto;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightDto {
    private Long id;
    private String flightNumber;
    private AirlineDto airline;
    private CityDto departureCity;
    private CityDto arrivalCity;
}