package com.services.dto;

import com.common.dto.AirlineDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightOwnerView {
    private Long id;
    private AirlineDto airline;
}
