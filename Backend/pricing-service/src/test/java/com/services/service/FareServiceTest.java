package com.services.service;

import com.common.dto.FareDto;
import com.services.entity.Fare;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.FareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FareServiceTest {

    @Mock
    private FareRepository fareRepository;

    @InjectMocks
    private FareService fareService;

    @Test
    void createFareSavesAndReturnsDto() {
        FareDto request = new FareDto(null, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD");
        Fare saved = new Fare(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD");
        when(fareRepository.save(any(Fare.class))).thenReturn(saved);

        FareDto result = fareService.createFare(request);

        assertEquals(1L, result.getId());
        assertEquals(BigDecimal.valueOf(250), result.getPrice());
    }

    @Test
    void getFareByIdReturnsDtoWhenFound() {
        Fare fare = new Fare(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD");
        when(fareRepository.findById(1L)).thenReturn(Optional.of(fare));

        FareDto result = fareService.getFareById(1L);

        assertEquals("ECONOMY", result.getCabinClass());
    }

    @Test
    void getFareByIdThrowsWhenMissing() {
        when(fareRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> fareService.getFareById(99L));
    }

    @Test
    void getAllFaresMapsEveryRow() {
        when(fareRepository.findAll()).thenReturn(List.of(
                new Fare(1L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"),
                new Fare(2L, 1L, "BUSINESS", BigDecimal.valueOf(800), "USD")));

        List<FareDto> result = fareService.getAllFares();

        assertEquals(2, result.size());
        assertEquals("BUSINESS", result.get(1).getCabinClass());
    }
}
