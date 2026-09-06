package com.services.config;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Spring Boot 4.0.2 ships no built-in Kafka health contributor (verified by inspecting
 * spring-boot-actuator-autoconfigure and spring-boot-kafka - neither contains a Kafka-related
 * health class), unlike the DataSource one that registers automatically. Without this, a broken
 * Kafka connection would leave /actuator/health reporting UP even though the outbox relay and
 * every consumer here are actually degraded.
 */
@Component
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // Not try-with-resources: Admin's default close() can block far longer than the 3s
        // describeCluster timeout while an unreachable broker's connection attempt is still
        // in flight - closing with an explicit short duration keeps this check fast even when
        // Kafka is genuinely down, which is the whole point of a health check.
        Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties());
        try {
            String clusterId = admin.describeCluster().clusterId().get(3, TimeUnit.SECONDS);
            builder.up().withDetail("clusterId", clusterId);
        } finally {
            admin.close(Duration.ofSeconds(2));
        }
    }
}
