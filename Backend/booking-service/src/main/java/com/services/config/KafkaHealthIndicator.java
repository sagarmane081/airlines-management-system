package com.services.config;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Spring Boot 4.0.2 ships no built-in Kafka health contributor - see payment-service's
 * KafkaHealthIndicator for the full reasoning, identical here.
 */
@Component
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // Not try-with-resources - see payment-service's KafkaHealthIndicator for why.
        Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties());
        try {
            String clusterId = admin.describeCluster().clusterId().get(3, TimeUnit.SECONDS);
            builder.up().withDetail("clusterId", clusterId);
        } finally {
            admin.close(Duration.ofSeconds(2));
        }
    }
}
