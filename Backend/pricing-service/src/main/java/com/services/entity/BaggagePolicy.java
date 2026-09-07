package com.services.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "baggage_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaggagePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal cabinBaggageAllowanceKg;

    private BigDecimal checkedBaggageAllowanceKg;

    private Integer checkedBaggagePieces;

    private BigDecimal extraBaggageFeePerKg;

    @OneToOne
    @JoinColumn(name = "fare_id")
    private Fare fare;
}
