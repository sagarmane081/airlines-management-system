package com.services.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row per BookingConfirmedEvent still owed to Kafka. Written in the same transaction as the
 * Booking status update it describes - see payment-service's OutboxEvent/OutboxRelay for the full
 * reasoning, identical here.
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

    private Long bookingId;

    private Long flightInstanceId;

    private Long seatInstanceId;

    private boolean published;

    private LocalDateTime createdAt;
}
