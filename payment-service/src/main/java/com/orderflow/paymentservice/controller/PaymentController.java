package com.orderflow.paymentservice.controller;

import com.orderflow.paymentservice.dto.PaymentResponse;
import com.orderflow.paymentservice.exception.NotFoundException;
import com.orderflow.paymentservice.repository.PaymentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated, demo/debugging only - not routed through the api-gateway in this phase. */
@RestController
@RequestMapping("/internal/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/{orderId}")
    public PaymentResponse getByOrderId(@PathVariable Long orderId) {
        return paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new NotFoundException("No payment found for orderId: " + orderId));
    }
}
