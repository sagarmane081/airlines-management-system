package com.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AirlineDto {
    private Long id;
    private String name;
    private String iataCode;
    private CityDto headquartersCity;
    private Long ownerId;
}