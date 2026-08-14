package com.orderflow.inventoryservice.service;

import com.orderflow.inventoryservice.entity.Inventory;
import com.orderflow.inventoryservice.entity.InventoryReservation;
import com.orderflow.inventoryservice.entity.Product;
import com.orderflow.inventoryservice.kafka.event.OrderCreatedEvent;
import com.orderflow.inventoryservice.repository.InventoryRepository;
import com.orderflow.inventoryservice.repository.InventoryReservationRepository;
import com.orderflow.inventoryservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * All-or-nothing multi-item stock reservation for an order.created event. Every product row
 * touched is pessimistic-locked (SELECT ... FOR UPDATE via InventoryRepository) before any
 * decision is made; if any item comes up short, nothing is decremented and no
 * InventoryReservation rows are written for this order.
 */
@Service
public class InventoryReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final ProductRepository productRepository;

    public InventoryReservationService(InventoryRepository inventoryRepository,
                                        InventoryReservationRepository reservationRepository,
                                        ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ReservationOutcome reserve(Long orderId, List<OrderCreatedEvent.Item> items) {
        // Aggregate by productId first so an order that lists the same product on more than
        // one line is checked against its true combined requirement, not line-by-line.
        Map<Long, Integer> requiredByProduct = items.stream()
                .collect(Collectors.groupingBy(OrderCreatedEvent.Item::productId, LinkedHashMap::new,
                        Collectors.summingInt(OrderCreatedEvent.Item::quantity)));

        Map<Long, Inventory> lockedByProduct = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : requiredByProduct.entrySet()) {
            Long productId = entry.getKey();
            int required = entry.getValue();
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId).orElse(null);
            int available = inventory == null ? 0 : inventory.getQuantityAvailable();
            if (inventory == null || available < required) {
                return ReservationOutcome.failure(shortageReason(productId, required, available));
            }
            lockedByProduct.put(productId, inventory);
        }

        for (OrderCreatedEvent.Item item : items) {
            lockedByProduct.get(item.productId()).decrease(item.quantity());
            reservationRepository.save(new InventoryReservation(orderId, item.productId(), item.quantity()));
        }
        return ReservationOutcome.succeeded();
    }

    private String shortageReason(Long productId, int required, int available) {
        String sku = productRepository.findById(productId).map(Product::getSku).orElse(null);
        String label = sku != null ? "SKU " + sku : "product " + productId;
        return "Insufficient stock for " + label + ": requested " + required + ", available " + available;
    }
}
