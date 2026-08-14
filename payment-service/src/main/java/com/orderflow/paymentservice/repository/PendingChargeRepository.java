package com.orderflow.paymentservice.repository;

import com.orderflow.paymentservice.entity.PendingCharge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingChargeRepository extends JpaRepository<PendingCharge, Long> {
}
