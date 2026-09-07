package com.services.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The actual bookable price/availability of an Ancillary catalog item on one specific Flight -
 * this is what lets "extra baggage" cost different amounts on different routes instead of being
 * one flat airline-wide price. Ancillary stays the reusable catalog definition; this is the join.
 */
@Entity
@Table(name = "flight_ancillaries", uniqueConstraints = @UniqueConstraint(columnNames = {"flight_id", "ancillary_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightAncillary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long flightId;

    @ManyToOne
    @JoinColumn(name = "ancillary_id")
    private Ancillary ancillary;

    private BigDecimal price;

    private boolean available;
}
