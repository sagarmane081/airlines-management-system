package com.services.service;

import com.common.event.BookingConfirmedEvent;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Message.creator(...) is a static Twilio SDK call, not an injected collaborator - mocked via
 * Mockito's static mocking (the default inline mock maker since Mockito 5, no extra dependency
 * needed) rather than the usual constructor-injection @Mock pattern every other service test in
 * this codebase uses, since there's no interface here to substitute.
 */
class SmsServiceTest {

    private SmsService smsService;
    private MockedStatic<Message> messageStatic;

    @BeforeEach
    void setUp() {
        // fromPhoneNumber is a plain constructor-injected String (not TwilioConstant), so this
        // test never touches a real Twilio env var - same reasoning as EmailServiceTest supplying
        // a literal fromAddress instead of reading real mail config.
        smsService = new SmsService("+15559999999");
        messageStatic = mockStatic(Message.class);
    }

    @AfterEach
    void tearDown() {
        messageStatic.close();
    }

    @Test
    void sendsSmsToCustomerPhoneWithBookingDetails() {
        MessageCreator creator = mock(MessageCreator.class);
        when(creator.create()).thenReturn(mock(Message.class));
        messageStatic.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                .thenReturn(creator);

        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L, 21L), "customer@example.com", "+15550001111");

        smsService.sendBookingConfirmationSms(event);

        messageStatic.verify(() -> Message.creator(
                argThat((PhoneNumber to) -> "+15550001111".equals(to.getEndpoint())),
                argThat((PhoneNumber from) -> "+15559999999".equals(from.getEndpoint())),
                argThat((String body) -> body.contains("#1") && body.contains("10"))));
        verify(creator, times(1)).create();
    }

    @Test
    void skipsSendingWhenCustomerPhoneIsMissing() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(1L, 10L, List.of(20L), "customer@example.com", null);

        smsService.sendBookingConfirmationSms(event);

        messageStatic.verifyNoInteractions();
    }
}
