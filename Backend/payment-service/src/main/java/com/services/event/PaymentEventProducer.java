package com.services.event;

import com.common.event.PaymentCompletedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final String TOPIC = "payment.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PaymentCompletedEvent event) {
        kafkaTemplate.send(TOPIC, event.getBookingId().toString(), event);
    }
}
