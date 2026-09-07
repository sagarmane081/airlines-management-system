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
@Table(name = "cabin_classes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CabinClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private CabinClassType name;

    private Integer startRow;

    private Integer endRow;

    private Integer seatsPerRow;

    private Integer seatPitchInches;

    @ManyToOne
    @JoinColumn(name = "seat_map_id")
    private SeatMap seatMap;
}
