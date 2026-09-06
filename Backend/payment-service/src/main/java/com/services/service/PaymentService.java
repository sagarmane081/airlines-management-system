package com.services.service;

import com.common.dto.PaymentDto;
import com.common.event.PaymentCompletedEvent;
import com.services.entity.Payment;
import com.services.entity.PaymentStatus;
import com.services.event.PaymentEventProducer;
import com.services.exception.ResourceNotFoundException;
import com.services.mapper.PaymentMapper;
import com.services.repository.PaymentRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentService(PaymentRepository paymentRepository, PaymentEventProducer paymentEventProducer) {
        this.paymentRepository = paymentRepository;
        this.paymentEventProducer = paymentEventProducer;
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

    public PaymentDto confirmPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));

        payment.setStatus(PaymentStatus.SUCCESS);
        Payment saved = paymentRepository.save(payment);

        paymentEventProducer.publish(new PaymentCompletedEvent(saved.getId(), saved.getBookingId(), saved.getAmount()));

        return PaymentMapper.toDto(saved);
    }
}
