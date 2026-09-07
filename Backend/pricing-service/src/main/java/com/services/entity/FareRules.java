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
@Table(name = "fare_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FareRules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean refundable;

    private boolean changeable;

    private BigDecimal cancellationFee;

    private BigDecimal changeFee;

    private Integer refundDeadlineHours;

    private Integer changeDeadlineHours;

    @OneToOne
    @JoinColumn(name = "fare_id")
    private Fare fare;
}
