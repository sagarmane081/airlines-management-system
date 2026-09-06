package com.services.service;

import com.common.dto.AirlineDto;
import com.services.client.AirlineClient;
import com.services.dto.AncillaryDto;
import com.services.entity.Ancillary;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.AncillaryMapper;
import com.services.repository.AncillaryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AncillaryService {

    private static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";
    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private final AncillaryRepository ancillaryRepository;
    private final AirlineClient airlineClient;

    public AncillaryService(AncillaryRepository ancillaryRepository, AirlineClient airlineClient) {
        this.ancillaryRepository = ancillaryRepository;
        this.airlineClient = airlineClient;
    }

    public AncillaryDto createAncillary(AncillaryDto ancillaryDto, Long requesterId, String requesterRole) {
        if (!ROLE_AIRLINE_OWNER.equals(requesterRole) && !ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("Only " + ROLE_AIRLINE_OWNER + " or " + ROLE_SYSTEM_ADMIN + " can create ancillaries");
        }
        AirlineDto airline = airlineClient.getAirlineById(ancillaryDto.getAirlineId());
        requireAirlineOwnership(airline, requesterId, requesterRole);

        Ancillary saved = ancillaryRepository.save(AncillaryMapper.toEntity(ancillaryDto));
        return AncillaryMapper.toDto(saved);
    }

    private void requireAirlineOwnership(AirlineDto airline, Long requesterId, String requesterRole) {
        if (ROLE_SYSTEM_ADMIN.equals(requesterRole)) {
            return;
        }
        boolean isOwner = airline.getOwnerId() != null && airline.getOwnerId().equals(requesterId);
        if (!isOwner) {
            throw new ForbiddenException("Airline " + airline.getId() + " is not owned by the requesting user");
        }
    }

    public AncillaryDto getAncillaryById(Long id) {
        Ancillary ancillary = ancillaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ancillary not found with id: " + id));
        return AncillaryMapper.toDto(ancillary);
    }

    public List<AncillaryDto> getAllAncillaries() {
        return ancillaryRepository.findAll().stream().map(AncillaryMapper::toDto).toList();
    }
}
