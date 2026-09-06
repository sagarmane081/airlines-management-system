package com.services.mapper;

import com.common.dto.PaymentDto;
import com.services.entity.Payment;

public class PaymentMapper {

    public static PaymentDto toDto(Payment payment) {
        PaymentDto dto = new PaymentDto();
        dto.setId(payment.getId());
        dto.setBookingId(payment.getBookingId());
        dto.setAmount(payment.getAmount());
        dto.setStatus(payment.getStatus().name());
        return dto;
    }
}
