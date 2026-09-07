package com.services.service;

import com.common.event.BookingConfirmedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @Test
    void sendsBookingConfirmationToCustomerEmail() {
        emailService = new EmailService(mailSender, "noreply@airline-learn.local");
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L, 21L), "customer@example.com", "+15550001111");

        emailService.sendBookingConfirmation(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertEquals("noreply@airline-learn.local", sent.getFrom());
        assertEquals("customer@example.com", sent.getTo()[0]);
        assertEquals("Booking Confirmed - #1", sent.getSubject());
    }

    @Test
    void skipsSendingWhenCustomerEmailIsMissing() {
        emailService = new EmailService(mailSender, "noreply@airline-learn.local");
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L), null, "+15550001111");

        emailService.sendBookingConfirmation(event);

        verifyNoInteractions(mailSender);
    }
}
