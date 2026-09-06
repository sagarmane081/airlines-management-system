package com.services.dto;

import com.services.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {
    private Long id;
    private Long userId;
    private Long flightInstanceId;
    private Long fareId;
    private Long seatInstanceId;
    private BigDecimal amount;
    private BookingStatus status;
    private Long paymentId;
}
