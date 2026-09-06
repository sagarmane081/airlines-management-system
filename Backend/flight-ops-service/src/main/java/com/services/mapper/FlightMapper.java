package com.services.mapper;

import com.common.dto.AirlineDto;
import com.common.dto.CityDto;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;

public class FlightMapper {

    public static FlightDto toDto(Flight flight, AirlineDto airline, CityDto departureCity, CityDto arrivalCity) {
        FlightDto dto = new FlightDto();
        dto.setId(flight.getId());
        dto.setFlightNumber(flight.getFlightNumber());
        dto.setAirline(airline);
        dto.setDepartureCity(departureCity);
        dto.setArrivalCity(arrivalCity);
        return dto;
    }

    public static FlightInstanceDto toDto(FlightInstance instance, FlightDto flightDto) {
        FlightInstanceDto dto = new FlightInstanceDto();
        dto.setId(instance.getId());
        dto.setFlight(flightDto);
        dto.setDepartureTime(instance.getDepartureTime());
        dto.setArrivalTime(instance.getArrivalTime());
        dto.setStatus(instance.getStatus());
        return dto;
    }

    public static Flight toEntity(FlightDto dto) {
        Flight flight = new Flight();
        flight.setId(dto.getId());
        flight.setFlightNumber(dto.getFlightNumber());
        flight.setAirlineId(dto.getAirline() != null ? dto.getAirline().getId() : null);
        flight.setDepartureCityId(dto.getDepartureCity() != null ? dto.getDepartureCity().getId() : null);
        flight.setArrivalCityId(dto.getArrivalCity() != null ? dto.getArrivalCity().getId() : null);
        return flight;
    }
}