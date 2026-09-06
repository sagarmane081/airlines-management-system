package com.services.event;

import com.common.event.BookingConfirmedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class BookingEventProducer {

    private static final String TOPIC = "booking.confirmed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Returns the send future rather than firing-and-forgetting, so OutboxRelay can block on it
     * and only mark the outbox row published once Kafka has actually acknowledged the record.
     */
    public CompletableFuture<SendResult<String, Object>> publish(BookingConfirmedEvent event) {
        return kafkaTemplate.send(TOPIC, event.getBookingId().toString(), event);
    }
}
