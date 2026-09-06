package com.services.service;

import com.services.dto.AncillaryDto;
import com.services.entity.Ancillary;
import com.services.entity.AncillaryType;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.AncillaryRepository;
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
class AncillaryServiceTest {

    @Mock
    private AncillaryRepository ancillaryRepository;

    @InjectMocks
    private AncillaryService ancillaryService;

    @Test
    void createAncillarySavesAndReturnsDto() {
        AncillaryDto request = new AncillaryDto(null, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE);
        Ancillary saved = new Ancillary(1L, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE);
        when(ancillaryRepository.save(any(Ancillary.class))).thenReturn(saved);

        AncillaryDto result = ancillaryService.createAncillary(request);

        assertEquals(1L, result.getId());
        assertEquals(AncillaryType.BAGGAGE, result.getType());
    }

    @Test
    void getAncillaryByIdReturnsDtoWhenFound() {
        Ancillary ancillary = new Ancillary(1L, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE);
        when(ancillaryRepository.findById(1L)).thenReturn(Optional.of(ancillary));

        AncillaryDto result = ancillaryService.getAncillaryById(1L);

        assertEquals("Extra bag", result.getName());
    }

    @Test
    void getAncillaryByIdThrowsWhenMissing() {
        when(ancillaryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ancillaryService.getAncillaryById(99L));
    }

    @Test
    void getAllAncillariesMapsEveryRow() {
        when(ancillaryRepository.findAll()).thenReturn(List.of(
                new Ancillary(1L, "Extra bag", "20kg", BigDecimal.valueOf(30), AncillaryType.BAGGAGE),
                new Ancillary(2L, "Wifi", "In-flight wifi", BigDecimal.valueOf(15), AncillaryType.WIFI)));

        List<AncillaryDto> result = ancillaryService.getAllAncillaries();

        assertEquals(2, result.size());
        assertEquals(AncillaryType.WIFI, result.get(1).getType());
    }
}
