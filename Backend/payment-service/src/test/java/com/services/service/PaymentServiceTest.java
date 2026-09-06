package com.services.service;

import com.common.dto.PaymentDto;
import com.services.entity.OutboxEvent;
import com.services.entity.Payment;
import com.services.entity.PaymentStatus;
import com.services.exception.ResourceNotFoundException;
import com.services.repository.OutboxEventRepository;
import com.services.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void initiatePaymentSavesAsPending() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentDto request = new PaymentDto(null, 10L, BigDecimal.valueOf(250), null);
        PaymentDto result = paymentService.initiatePayment(request);

        assertEquals("PENDING", result.getStatus());
        assertEquals(10L, result.getBookingId());
    }

    @Test
    void getPaymentByIdThrowsWhenMissing() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getPaymentById(99L));
    }

    @Test
    void confirmPaymentFlipsToSuccessAndWritesOutboxRowOnce() {
        Payment payment = new Payment(1L, 10L, BigDecimal.valueOf(250), PaymentStatus.PENDING);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentDto result = paymentService.confirmPayment(1L);

        assertEquals("SUCCESS", result.getStatus());
        verify(outboxEventRepository, times(1)).save(argThat((OutboxEvent e) ->
                e.getPaymentId().equals(1L) && e.getBookingId().equals(10L) && !e.isPublished()));
    }

    @Test
    void confirmingAnAlreadySuccessfulPaymentIsANoOp() {
        Payment payment = new Payment(1L, 10L, BigDecimal.valueOf(250), PaymentStatus.SUCCESS);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentDto result = paymentService.confirmPayment(1L);

        assertEquals("SUCCESS", result.getStatus());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }
}
