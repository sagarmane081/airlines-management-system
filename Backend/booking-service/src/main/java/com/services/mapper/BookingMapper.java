package com.services.mapper;

import com.services.dto.BookingDto;
import com.services.entity.Booking;

public class BookingMapper {

    public static BookingDto toDto(Booking booking) {
        BookingDto dto = new BookingDto();
        dto.setId(booking.getId());
        dto.setFlightInstanceId(booking.getFlightInstanceId());
        dto.setFareId(booking.getFareId());
        dto.setSeatInstanceId(booking.getSeatInstanceId());
        dto.setAmount(booking.getAmount());
        dto.setStatus(booking.getStatus());
        dto.setPaymentId(booking.getPaymentId());
        return dto;
    }
}
