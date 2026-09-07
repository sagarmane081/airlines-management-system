package com.services.event;

import com.common.event.BookingConfirmedEvent;
import com.services.entity.OutboxEvent;
import com.services.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls for outbox rows nobody has successfully published yet and relays them to Kafka. A row is
 * only marked published after Kafka has actually acknowledged the record - if the process crashes
 * or Kafka is unreachable mid-relay, the row is simply retried on the next poll. That retry is
 * exactly why the downstream consumers (seat-service, notification-service) had to be made
 * idempotent already.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final BookingEventProducer bookingEventProducer;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, BookingEventProducer bookingEventProducer) {
        this.outboxEventRepository = outboxEventRepository;
        this.bookingEventProducer = bookingEventProducer;
    }

    @Scheduled(fixedDelay = 3000)
    public void relayUnpublishedEvents() {
        List<OutboxEvent> unpublished = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent outboxEvent : unpublished) {
            relayOne(outboxEvent);
        }
    }

    @Transactional
    public void relayOne(OutboxEvent outboxEvent) {
        try {
            bookingEventProducer.publish(new BookingConfirmedEvent(
                            outboxEvent.getBookingId(), outboxEvent.getFlightInstanceId(), outboxEvent.getSeatInstanceIds(),
                            outboxEvent.getCustomerEmail()))
                    .get(5, TimeUnit.SECONDS);
            outboxEvent.setPublished(true);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.warn("Failed to relay outbox event id={}, will retry next poll: {}", outboxEvent.getId(), e.getMessage());
        }
    }
}
