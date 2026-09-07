package com.services.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Named seatRow, not rowNumber - ROW_NUMBER is a reserved keyword in MySQL 8.0 (added for
    // window functions), and Hibernate's schema update silently failed to create this table when
    // the column was named row_number, logging a WARN instead of a fatal startup error.
    private Integer seatRow;

    private String columnLetter;

    @Enumerated(EnumType.STRING)
    private SeatType seatType;

    private boolean exitRow;

    @ManyToOne
    @JoinColumn(name = "cabin_class_id")
    private CabinClass cabinClass;

    // Derived, not persisted - keeps the display form (e.g. "12A") in sync with seatRow/
    // columnLetter by construction, instead of risking a stored copy drifting out of sync.
    public String getSeatNumber() {
        return seatRow + columnLetter;
    }
}
