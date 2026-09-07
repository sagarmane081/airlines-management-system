package com.services.event;

import com.common.event.BookingConfirmedEvent;
import com.services.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingConfirmedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmedEventConsumer.class);

    private final EmailService emailService;

    public BookingConfirmedEventConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "booking.confirmed", groupId = "notification-service-group")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Sending booking confirmation notification: bookingId={}, flightInstanceId={}, seatInstanceIds={}",
                event.getBookingId(), event.getFlightInstanceId(), event.getSeatInstanceIds());

        try {
            emailService.sendBookingConfirmation(event);
        } catch (RuntimeException e) {
            // A redelivered event already produces a duplicate log line above by design (Stage 10)
            // - a duplicate email on redelivery is the same accepted trade-off, not a new gap.
            log.warn("Failed to send booking confirmation email for bookingId={}: {}", event.getBookingId(), e.getMessage());
        }
    }
}
