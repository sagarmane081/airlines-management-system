package com.services.service;

import com.services.dto.SeatInstanceDto;
import com.services.entity.SeatInstance;
import com.services.entity.SeatStatus;
import com.services.exception.SeatNotAvailableException;
import com.services.mapper.SeatInstanceMapper;
import com.services.repository.SeatInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatInstanceService {

    private final SeatInstanceRepository seatInstanceRepository;

    public SeatInstanceService(SeatInstanceRepository seatInstanceRepository) {
        this.seatInstanceRepository = seatInstanceRepository;
    }

    public SeatInstanceDto createSeatInstance(SeatInstanceDto seatInstanceDto) {
        SeatInstance saved = seatInstanceRepository.save(SeatInstanceMapper.toEntity(seatInstanceDto));
        return SeatInstanceMapper.toDto(saved);
    }

    public SeatInstanceDto getSeatInstanceById(Long id) {
        SeatInstance seatInstance = seatInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SeatInstance not found with id: " + id));
        return SeatInstanceMapper.toDto(seatInstance);
    }

    public List<SeatInstanceDto> getAllSeatInstances() {
        return seatInstanceRepository.findAll().stream().map(SeatInstanceMapper::toDto).toList();
    }

    @Transactional
    public SeatInstanceDto holdSeat(Long id) {
        SeatInstance seatInstance = seatInstanceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("SeatInstance not found with id: " + id));

        if (seatInstance.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatNotAvailableException(
                    "Seat " + id + " is not available (status: " + seatInstance.getStatus() + ")");
        }

        seatInstance.setStatus(SeatStatus.HELD);
        SeatInstance saved = seatInstanceRepository.save(seatInstance);
        return SeatInstanceMapper.toDto(saved);
    }
}
