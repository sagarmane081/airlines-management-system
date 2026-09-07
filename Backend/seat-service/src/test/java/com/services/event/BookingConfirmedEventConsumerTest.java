package com.services.event;

import com.common.event.BookingConfirmedEvent;
import com.services.entity.SeatInstance;
import com.services.entity.SeatStatus;
import com.services.repository.SeatInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kafka is at-least-once, not exactly-once: this proves a redelivered BookingConfirmedEvent
 * does not re-save the seat a second time.
 */
@ExtendWith(MockitoExtension.class)
class BookingConfirmedEventConsumerTest {

    @Mock
    private SeatInstanceRepository seatInstanceRepository;

    @Test
    void redeliveredEventIsANoOp() {
        BookingConfirmedEventConsumer consumer = new BookingConfirmedEventConsumer(seatInstanceRepository);

        SeatInstance seat = new SeatInstance();
        seat.setId(1L);
        seat.setFlightInstanceId(10L);
        seat.setSeatNumber("1A");
        seat.setCabinClass("ECONOMY");
        seat.setStatus(SeatStatus.HELD);

        when(seatInstanceRepository.findById(1L)).thenReturn(Optional.of(seat));

        BookingConfirmedEvent event = new BookingConfirmedEvent(100L, 10L, List.of(1L), "customer@example.com");

        consumer.onBookingConfirmed(event);
        consumer.onBookingConfirmed(event); // simulated redelivery of the same message

        assertEquals(SeatStatus.BOOKED, seat.getStatus());
        verify(seatInstanceRepository, times(1)).save(any(SeatInstance.class));
    }

    @Test
    void marksEverySeatInAMultiPassengerBookingBooked() {
        BookingConfirmedEventConsumer consumer = new BookingConfirmedEventConsumer(seatInstanceRepository);

        SeatInstance seat1 = new SeatInstance();
        seat1.setId(1L);
        seat1.setStatus(SeatStatus.HELD);
        SeatInstance seat2 = new SeatInstance();
        seat2.setId(2L);
        seat2.setStatus(SeatStatus.HELD);

        when(seatInstanceRepository.findById(1L)).thenReturn(Optional.of(seat1));
        when(seatInstanceRepository.findById(2L)).thenReturn(Optional.of(seat2));

        consumer.onBookingConfirmed(new BookingConfirmedEvent(100L, 10L, List.of(1L, 2L), "customer@example.com"));

        assertEquals(SeatStatus.BOOKED, seat1.getStatus());
        assertEquals(SeatStatus.BOOKED, seat2.getStatus());
        verify(seatInstanceRepository, times(2)).save(any(SeatInstance.class));
    }
}
