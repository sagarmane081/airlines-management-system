package com.services.event;

import com.common.event.BookingConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingConfirmedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmedEventConsumer.class);

    @KafkaListener(topics = "booking.confirmed", groupId = "notification-service-group")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Sending booking confirmation notification: bookingId={}, flightInstanceId={}, seatInstanceId={}",
                event.getBookingId(), event.getFlightInstanceId(), event.getSeatInstanceId());
    }
}
