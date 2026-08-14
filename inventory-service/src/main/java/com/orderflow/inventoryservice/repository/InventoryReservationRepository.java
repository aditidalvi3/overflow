package com.orderflow.inventoryservice.repository;

import com.orderflow.inventoryservice.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByOrderId(Long orderId);
}
