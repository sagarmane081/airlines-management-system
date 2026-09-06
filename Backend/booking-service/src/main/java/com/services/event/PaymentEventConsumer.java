package com.services.event;

import com.common.event.BookingConfirmedEvent;
import com.common.event.PaymentCompletedEvent;
import com.services.entity.Booking;
import com.services.entity.BookingStatus;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.BookingRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private final BookingRepository bookingRepository;
    private final BookingEventProducer bookingEventProducer;

    public PaymentEventConsumer(BookingRepository bookingRepository, BookingEventProducer bookingEventProducer) {
        this.bookingRepository = bookingRepository;
        this.bookingEventProducer = bookingEventProducer;
    }

    @KafkaListener(topics = "payment.completed", groupId = "booking-service-group")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Booking booking = bookingRepository.findById(event.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + event.getBookingId()));

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);

        bookingEventProducer.publish(
                new BookingConfirmedEvent(saved.getId(), saved.getFlightInstanceId(), saved.getSeatInstanceId()));
    }
}
