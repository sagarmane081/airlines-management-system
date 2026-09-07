package com.services.service;

import com.common.event.BookingConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${notification.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendBookingConfirmation(BookingConfirmedEvent event) {
        if (event.getCustomerEmail() == null || event.getCustomerEmail().isBlank()) {
            log.warn("No customer email on BookingConfirmedEvent for bookingId={}, skipping email", event.getBookingId());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(event.getCustomerEmail());
        message.setSubject("Booking Confirmed - #" + event.getBookingId());
        message.setText("""
                Your booking is confirmed!

                Booking ID: %d
                Flight Instance: %d
                Seats: %s

                Thank you for booking with us.
                """.formatted(event.getBookingId(), event.getFlightInstanceId(), event.getSeatInstanceIds()));

        mailSender.send(message);
    }
}
