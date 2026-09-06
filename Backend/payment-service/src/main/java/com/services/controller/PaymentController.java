package com.services.controller;

import com.common.dto.PaymentDto;
import com.services.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentDto> initiatePayment(@RequestBody PaymentDto paymentDto) {
        return new ResponseEntity<>(paymentService.initiatePayment(paymentDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PaymentDto> confirmPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.confirmPayment(id));
    }
}
