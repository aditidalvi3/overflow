package com.orderflow.paymentservice.repository;

import com.orderflow.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
