package com.services.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per PaymentCompletedEvent still owed to Kafka. Written in the same transaction as the
 * Payment update it describes, so the two either both commit or both roll back - no window where
 * the DB says SUCCESS but nobody downstream was ever told. Typed to this one event shape rather
 * than a generic JSON payload + type discriminator, since payment-service only ever emits this
 * one kind of event.
 */
@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paymentId;

    private Long bookingId;

    private BigDecimal amount;

    private boolean published;

    private LocalDateTime createdAt;
}
