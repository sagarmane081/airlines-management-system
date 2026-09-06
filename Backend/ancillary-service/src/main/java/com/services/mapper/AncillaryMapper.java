package com.services.mapper;

import com.services.dto.AncillaryDto;
import com.services.entity.Ancillary;

public class AncillaryMapper {

    public static AncillaryDto toDto(Ancillary ancillary) {
        AncillaryDto dto = new AncillaryDto();
        dto.setId(ancillary.getId());
        dto.setName(ancillary.getName());
        dto.setDescription(ancillary.getDescription());
        dto.setPrice(ancillary.getPrice());
        dto.setType(ancillary.getType());
        return dto;
    }

    public static Ancillary toEntity(AncillaryDto dto) {
        Ancillary ancillary = new Ancillary();
        ancillary.setId(dto.getId());
        ancillary.setName(dto.getName());
        ancillary.setDescription(dto.getDescription());
        ancillary.setPrice(dto.getPrice());
        ancillary.setType(dto.getType());
        return ancillary;
    }
}
