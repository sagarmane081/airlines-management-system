package com.services.dto;

import com.services.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {
    private Long id;
    private Long userId;
    private Long flightInstanceId;
    private Long fareId;
    private List<PassengerDto> passengers = new ArrayList<>();
    private BigDecimal amount;
    private BookingStatus status;
    private Long paymentId;
}
