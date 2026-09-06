package com.services.event;

import com.common.event.PaymentCompletedEvent;
import com.services.entity.Booking;
import com.services.entity.BookingStatus;
import com.services.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kafka is at-least-once, not exactly-once: this proves a redelivered PaymentCompletedEvent
 * does not reconfirm a booking or republish BookingConfirmedEvent a second time.
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventProducer bookingEventProducer;

    @Test
    void redeliveredEventIsANoOp() {
        PaymentEventConsumer consumer = new PaymentEventConsumer(bookingRepository, bookingEventProducer);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setFlightInstanceId(10L);
        booking.setSeatInstanceId(20L);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentCompletedEvent event = new PaymentCompletedEvent(100L, 1L, BigDecimal.TEN);

        consumer.onPaymentCompleted(event);
        consumer.onPaymentCompleted(event); // simulated redelivery of the same message

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        verify(bookingRepository, times(1)).save(any(Booking.class));
        verify(bookingEventProducer, times(1)).publish(any());
    }
}
