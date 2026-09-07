package com.services.mapper;

import com.common.dto.AircraftDto;
import com.common.dto.AirlineDto;
import com.common.dto.AirportDto;
import com.services.dto.FlightDto;
import com.services.dto.FlightInstanceDto;
import com.services.entity.Flight;
import com.services.entity.FlightInstance;

public class FlightMapper {

    public static FlightDto toDto(Flight flight, AirlineDto airline, AirportDto departureAirport, AirportDto arrivalAirport) {
        FlightDto dto = new FlightDto();
        dto.setId(flight.getId());
        dto.setFlightNumber(flight.getFlightNumber());
        dto.setAirline(airline);
        dto.setDepartureAirport(departureAirport);
        dto.setArrivalAirport(arrivalAirport);
        return dto;
    }

    public static FlightInstanceDto toDto(FlightInstance instance, FlightDto flightDto, AircraftDto aircraft) {
        FlightInstanceDto dto = new FlightInstanceDto();
        dto.setId(instance.getId());
        dto.setFlight(flightDto);
        dto.setDepartureTime(instance.getDepartureTime());
        dto.setArrivalTime(instance.getArrivalTime());
        dto.setStatus(instance.getStatus());
        dto.setAircraft(aircraft);
        dto.setFlightScheduleId(instance.getFlightSchedule() != null ? instance.getFlightSchedule().getId() : null);
        return dto;
    }

    public static Flight toEntity(FlightDto dto) {
        Flight flight = new Flight();
        flight.setId(dto.getId());
        flight.setFlightNumber(dto.getFlightNumber());
        flight.setAirlineId(dto.getAirline() != null ? dto.getAirline().getId() : null);
        flight.setDepartureAirportId(dto.getDepartureAirport() != null ? dto.getDepartureAirport().getId() : null);
        flight.setArrivalAirportId(dto.getArrivalAirport() != null ? dto.getArrivalAirport().getId() : null);
        return flight;
    }
}
