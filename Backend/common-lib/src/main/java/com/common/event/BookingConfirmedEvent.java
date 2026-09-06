package com.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {
    private Long bookingId;
    private Long flightInstanceId;
    private Long seatInstanceId;
}
