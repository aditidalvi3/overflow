package com.orderflow.inventoryservice.service;

import com.orderflow.inventoryservice.entity.InventoryReservation;
import com.orderflow.inventoryservice.repository.InventoryRepository;
import com.orderflow.inventoryservice.repository.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Compensating release, used by both the payment.failed and order.cancelled listeners:
 * look up this order's InventoryReservation rows (inventory-service's own private state -
 * neither payment.failed nor order.cancelled carries the item list) and give the reserved
 * quantities back.
 */
@Service
public class InventoryReleaseService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;

    public InventoryReleaseService(InventoryReservationRepository reservationRepository,
                                    InventoryRepository inventoryRepository) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public void release(Long orderId) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        for (InventoryReservation reservation : reservations) {
            inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                    .ifPresent(inventory -> inventory.increase(reservation.getQuantity()));
        }
    }
}
