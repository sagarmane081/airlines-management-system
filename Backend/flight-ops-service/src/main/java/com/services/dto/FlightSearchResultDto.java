package com.services.dto;

import com.common.dto.FareDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchResultDto {
    private FlightInstanceDto flightInstance;
    // Null when no fare matched a price/cabin-class filter and no such filter was active, or when
    // the flight simply has no fares set up yet - the flight still shows up so browsing works even
    // for not-yet-priced routes, just without a price to display.
    private FareDto fare;
}
