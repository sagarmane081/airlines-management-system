package com.services.service;

import com.common.dto.FareDto;
import com.services.entity.Fare;
import com.services.mapper.FareMapper;
import com.services.repository.FareRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FareService {

    private final FareRepository fareRepository;

    public FareService(FareRepository fareRepository) {
        this.fareRepository = fareRepository;
    }

    public FareDto createFare(FareDto fareDto) {
        Fare saved = fareRepository.save(FareMapper.toEntity(fareDto));
        return FareMapper.toDto(saved);
    }

    public FareDto getFareById(Long id) {
        Fare fare = fareRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fare not found with id: " + id));
        return FareMapper.toDto(fare);
    }

    public List<FareDto> getAllFares() {
        return fareRepository.findAll().stream().map(FareMapper::toDto).toList();
    }
}