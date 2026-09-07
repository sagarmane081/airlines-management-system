package com.services.mapper;

import com.services.dto.BookingDto;
import com.services.entity.Booking;

public class BookingMapper {

    public static BookingDto toDto(Booking booking) {
        BookingDto dto = new BookingDto();
        dto.setId(booking.getId());
        dto.setUserId(booking.getUserId());
        dto.setUserEmail(booking.getUserEmail());
        dto.setUserPhone(booking.getUserPhone());
        dto.setFlightInstanceId(booking.getFlightInstanceId());
        dto.setFareId(booking.getFareId());
        dto.setPassengers(booking.getPassengers().stream().map(PassengerMapper::toDto).toList());
        dto.setAmount(booking.getAmount());
        dto.setStatus(booking.getStatus());
        dto.setPaymentId(booking.getPaymentId());
        return dto;
    }
}
