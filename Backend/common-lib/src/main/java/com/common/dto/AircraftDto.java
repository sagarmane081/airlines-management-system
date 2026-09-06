package com.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AircraftDto {
    private Long id;
    private String registrationNumber;
    private String model;
    private String manufacturer;
    private Integer totalSeats;
    private String status;
    private AirlineDto airline;
}
