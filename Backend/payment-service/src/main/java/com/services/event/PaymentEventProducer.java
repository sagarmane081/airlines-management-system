package com.services.event;

import com.common.event.PaymentCompletedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class PaymentEventProducer {

    private static final String TOPIC = "payment.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Returns the send future rather than firing-and-forgetting, so OutboxRelay can block on it
     * and only mark the outbox row published once Kafka has actually acknowledged the record.
     */
    public CompletableFuture<SendResult<String, Object>> publish(PaymentCompletedEvent event) {
        return kafkaTemplate.send(TOPIC, event.getBookingId().toString(), event);
    }
}
