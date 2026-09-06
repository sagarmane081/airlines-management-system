package com.services.event;

import com.common.event.BookingConfirmedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventProducer {

    private static final String TOPIC = "booking.confirmed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(BookingConfirmedEvent event) {
        kafkaTemplate.send(TOPIC, event.getBookingId().toString(), event);
    }
}
