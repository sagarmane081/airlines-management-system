package com.services.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // EAGER: the relay loads this row outside a transaction (relayUnpublishedEvents isn't
    // @Transactional), so the entity is already detached by the time relayOne's own transaction
    // starts - a LAZY collection would fail with "no session" instead of just fetching once here.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "outbox_event_seat_instance_ids", joinColumns = @JoinColumn(name = "outbox_event_id"))
    @Column(name = "seat_instance_id")
    private List<Long> seatInstanceIds = new ArrayList<>();

    private String customerEmail;

    private String customerPhone;

    private boolean published;

    private LocalDateTime createdAt;
}
