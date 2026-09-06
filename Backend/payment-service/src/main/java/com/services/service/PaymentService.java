package com.services.service;

import com.common.dto.PaymentDto;
import com.services.entity.OutboxEvent;
import com.services.entity.Payment;
import com.services.entity.PaymentStatus;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.PaymentMapper;
import com.services.repository.OutboxEventRepository;
import com.services.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;

    public PaymentService(PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    public PaymentDto initiatePayment(PaymentDto paymentDto) {
        Payment payment = new Payment();
        payment.setBookingId(paymentDto.getBookingId());
        payment.setAmount(paymentDto.getAmount());
        payment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);
        return PaymentMapper.toDto(saved);
    }

    public PaymentDto getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        return PaymentMapper.toDto(payment);
    }

    /**
     * Writes the PaymentCompletedEvent to the outbox in the same transaction as the status
     * update, instead of publishing to Kafka directly - see OutboxRelay for why.
     */
    @Transactional
    public PaymentDto confirmPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            // Already confirmed - a duplicate call (client retry, or eventually a redelivered
            // message) should be a no-op, not a second outbox row.
            return PaymentMapper.toDto(payment);
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        Payment saved = paymentRepository.save(payment);

        outboxEventRepository.save(new OutboxEvent(
                null, saved.getId(), saved.getBookingId(), saved.getAmount(), false, LocalDateTime.now()));

        return PaymentMapper.toDto(saved);
    }
}
