package com.orderflow.repository;

import com.orderflow.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
