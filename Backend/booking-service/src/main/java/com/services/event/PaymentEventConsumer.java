package com.services.event;

import com.common.event.PaymentCompletedEvent;
import com.services.entity.Booking;
import com.services.entity.BookingStatus;
import com.services.entity.OutboxEvent;
import com.services.entity.Passenger;
import com.services.entity.Ticket;
import com.services.entity.TicketStatus;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.BookingRepository;
import com.services.repository.OutboxEventRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class PaymentEventConsumer {

    private final BookingRepository bookingRepository;
    private final OutboxEventRepository outboxEventRepository;

    public PaymentEventConsumer(BookingRepository bookingRepository, OutboxEventRepository outboxEventRepository) {
        this.bookingRepository = bookingRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Writes the BookingConfirmedEvent to the outbox in the same transaction as the status
     * update, instead of publishing to Kafka directly - see OutboxRelay for why. Also issues one
     * Ticket per passenger here, since this is the one place that only runs once per booking
     * (guarded below) - issuing tickets anywhere else would risk duplicates on redelivery.
     */
    @Transactional
    @KafkaListener(topics = "payment.completed", groupId = "booking-service-group")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Booking booking = bookingRepository.findById(event.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + event.getBookingId()));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            // Kafka is at-least-once, not exactly-once - a redelivered PaymentCompletedEvent
            // must not create a second outbox row or issue a second ticket per passenger.
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        for (Passenger passenger : booking.getPassengers()) {
            Ticket ticket = new Ticket();
            ticket.setTicketNumber("TKT" + String.format("%010d", passenger.getId()));
            ticket.setStatus(TicketStatus.ISSUED);
            ticket.setIssuedAt(LocalDateTime.now());
            ticket.setPassenger(passenger);
            passenger.setTicket(ticket);
        }
        Booking saved = bookingRepository.save(booking);

        outboxEventRepository.save(new OutboxEvent(
                null, saved.getId(), saved.getFlightInstanceId(),
                saved.getPassengers().stream().map(Passenger::getSeatInstanceId).toList(),
                false, LocalDateTime.now()));
    }
}
