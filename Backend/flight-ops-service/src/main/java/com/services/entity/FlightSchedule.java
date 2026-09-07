package com.services.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A recurring pattern for a Flight - "AI101 operates Mon/Wed/Fri, 10:00-12:00 local, from
 * startDate to endDate." One FlightInstance per matching calendar date is materialized from this
 * via generateInstances, not auto-generated on save - a schedule describes intent, an instance is
 * a real bookable occurrence.
 */
@Entity
@Table(name = "flight_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "flight_id")
    private Flight flight;

    private LocalTime departureTime;

    private LocalTime arrivalTime;

    private LocalDate startDate;

    private LocalDate endDate;

    // EAGER: a handful of days always needed together with the schedule - same reasoning as
    // OutboxEvent.seatInstanceIds, avoids any risk of the lazy-detached-session gotcha from Stage 14.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "flight_schedule_operating_days", joinColumns = @JoinColumn(name = "flight_schedule_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> operatingDays = new HashSet<>();
}
