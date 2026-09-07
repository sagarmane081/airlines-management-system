package com.services.service;

import com.common.event.BookingConfirmedEvent;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final String fromPhoneNumber;

    public SmsService(@Value("${twilio.from-phone-number}") String fromPhoneNumber) {
        this.fromPhoneNumber = fromPhoneNumber;
    }

    public void sendBookingConfirmationSms(BookingConfirmedEvent event) {
        if (event.getCustomerPhone() == null || event.getCustomerPhone().isBlank()) {
            log.warn("No customer phone on BookingConfirmedEvent for bookingId={}, skipping SMS", event.getBookingId());
            return;
        }

        Message.creator(
                new PhoneNumber(event.getCustomerPhone()),
                new PhoneNumber(fromPhoneNumber),
                "Your booking #%d is confirmed! Flight instance %d, seats %s. Thank you for booking with us."
                        .formatted(event.getBookingId(), event.getFlightInstanceId(), event.getSeatInstanceIds())
        ).create();
    }
}
