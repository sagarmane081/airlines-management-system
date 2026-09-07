package com.services.event;

import com.common.event.BookingConfirmedEvent;
import com.services.service.EmailService;
import com.services.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingConfirmedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmedEventConsumer.class);

    private final EmailService emailService;
    private final SmsService smsService;

    public BookingConfirmedEventConsumer(EmailService emailService, SmsService smsService) {
        this.emailService = emailService;
        this.smsService = smsService;
    }

    @KafkaListener(topics = "booking.confirmed", groupId = "notification-service-group")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Sending booking confirmation notification: bookingId={}, flightInstanceId={}, seatInstanceIds={}",
                event.getBookingId(), event.getFlightInstanceId(), event.getSeatInstanceIds());

        // Each channel gets its own try/catch, deliberately independent - an SMTP outage
        // shouldn't prevent the SMS attempt, and a Twilio failure shouldn't prevent the email.
        // A redelivered event already produces a duplicate log line above by design (Stage 10) -
        // a duplicate email/SMS on redelivery is the same accepted trade-off, not a new gap.
        try {
            emailService.sendBookingConfirmation(event);
        } catch (RuntimeException e) {
            log.warn("Failed to send booking confirmation email for bookingId={}: {}", event.getBookingId(), e.getMessage());
        }

        try {
            smsService.sendBookingConfirmationSms(event);
        } catch (RuntimeException e) {
            log.warn("Failed to send booking confirmation SMS for bookingId={}: {}", event.getBookingId(), e.getMessage());
        }
    }
}
