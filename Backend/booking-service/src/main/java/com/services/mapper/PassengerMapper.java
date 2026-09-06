package com.services.mapper;

import com.services.dto.PassengerDto;
import com.services.entity.Passenger;
import com.services.entity.Ticket;

public class PassengerMapper {

    public static Passenger toEntity(PassengerDto dto) {
        Passenger passenger = new Passenger();
        passenger.setFirstName(dto.getFirstName());
        passenger.setLastName(dto.getLastName());
        passenger.setDateOfBirth(dto.getDateOfBirth());
        passenger.setGender(dto.getGender());
        passenger.setPassportNumber(dto.getPassportNumber());
        passenger.setNationality(dto.getNationality());
        passenger.setSeatInstanceId(dto.getSeatInstanceId());
        return passenger;
    }

    public static PassengerDto toDto(Passenger passenger) {
        PassengerDto dto = new PassengerDto();
        dto.setId(passenger.getId());
        dto.setFirstName(passenger.getFirstName());
        dto.setLastName(passenger.getLastName());
        dto.setDateOfBirth(passenger.getDateOfBirth());
        dto.setGender(passenger.getGender());
        dto.setPassportNumber(passenger.getPassportNumber());
        dto.setNationality(passenger.getNationality());
        dto.setSeatInstanceId(passenger.getSeatInstanceId());

        Ticket ticket = passenger.getTicket();
        if (ticket != null) {
            dto.setTicketNumber(ticket.getTicketNumber());
            dto.setTicketStatus(ticket.getStatus());
        }
        return dto;
    }
}
