package com.services.service;

import com.services.dto.AncillaryDto;
import com.services.entity.Ancillary;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.AncillaryMapper;
import com.services.repository.AncillaryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AncillaryService {

    private final AncillaryRepository ancillaryRepository;

    public AncillaryService(AncillaryRepository ancillaryRepository) {
        this.ancillaryRepository = ancillaryRepository;
    }

    public AncillaryDto createAncillary(AncillaryDto ancillaryDto) {
        Ancillary saved = ancillaryRepository.save(AncillaryMapper.toEntity(ancillaryDto));
        return AncillaryMapper.toDto(saved);
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
