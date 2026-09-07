package com.services.mapper;

import com.services.dto.FlightAncillaryDto;
import com.services.entity.Ancillary;
import com.services.entity.FlightAncillary;

public class FlightAncillaryMapper {

    public static FlightAncillaryDto toDto(FlightAncillary flightAncillary) {
        FlightAncillaryDto dto = new FlightAncillaryDto();
        dto.setId(flightAncillary.getId());
        dto.setFlightId(flightAncillary.getFlightId());
        dto.setAncillary(AncillaryMapper.toDto(flightAncillary.getAncillary()));
        dto.setPrice(flightAncillary.getPrice());
        dto.setAvailable(flightAncillary.isAvailable());
        return dto;
    }

    public static FlightAncillary toEntity(FlightAncillaryDto dto, Ancillary ancillary) {
        FlightAncillary flightAncillary = new FlightAncillary();
        flightAncillary.setFlightId(dto.getFlightId());
        flightAncillary.setAncillary(ancillary);
        flightAncillary.setPrice(dto.getPrice());
        flightAncillary.setAvailable(Boolean.TRUE.equals(dto.getAvailable()));
        return flightAncillary;
    }
}
