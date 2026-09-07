package com.services.event;

import com.common.event.BookingConfirmedEvent;
import com.services.service.EmailService;
import com.services.service.SmsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingConfirmedEventConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private BookingConfirmedEventConsumer consumer;

    @Test
    void delegatesToEmailServiceForEveryEvent() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L), "customer@example.com", "+15550001111");

        consumer.onBookingConfirmed(event);

        verify(emailService, times(1)).sendBookingConfirmation(event);
    }

    @Test
    void delegatesToSmsServiceForEveryEvent() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L), "customer@example.com", "+15550001111");

        consumer.onBookingConfirmed(event);

        verify(smsService, times(1)).sendBookingConfirmationSms(event);
    }

    @Test
    void aFailedEmailSendDoesNotPropagate() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L), "customer@example.com", "+15550001111");
        doThrow(new RuntimeException("SMTP unreachable")).when(emailService).sendBookingConfirmation(event);

        consumer.onBookingConfirmed(event);
    }

    @Test
    void aFailedSmsSendDoesNotPropagate() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L), "customer@example.com", "+15550001111");
        doThrow(new RuntimeException("Twilio unreachable")).when(smsService).sendBookingConfirmationSms(event);

        consumer.onBookingConfirmed(event);
    }

    @Test
    void aFailedEmailSendDoesNotPreventTheSmsAttempt() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L), "customer@example.com", "+15550001111");
        doThrow(new RuntimeException("SMTP unreachable")).when(emailService).sendBookingConfirmation(event);

        consumer.onBookingConfirmed(event);

        verify(smsService, times(1)).sendBookingConfirmationSms(event);
    }

    @Test
    void aFailedSmsSendDoesNotPreventTheEmailAttempt() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L), "customer@example.com", "+15550001111");
        doThrow(new RuntimeException("Twilio unreachable")).when(smsService).sendBookingConfirmationSms(event);

        consumer.onBookingConfirmed(event);

        verify(emailService, times(1)).sendBookingConfirmation(event);
    }
}
