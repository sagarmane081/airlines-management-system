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
 * The actual bookable price/availability of a Meal on one specific Flight - same join shape as
 * FlightAncillary, for the same reason (an airline's meal catalog is reusable, its per-route
 * pricing and availability isn't).
 */
@Entity
@Table(name = "flight_meals", uniqueConstraints = @UniqueConstraint(columnNames = {"flight_id", "meal_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long flightId;

    @ManyToOne
    @JoinColumn(name = "meal_id")
    private Meal meal;

    private BigDecimal price;

    private boolean available;
}
