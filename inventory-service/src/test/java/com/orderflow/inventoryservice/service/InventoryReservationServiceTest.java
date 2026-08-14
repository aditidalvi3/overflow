package com.orderflow.inventoryservice.service;

import com.orderflow.inventoryservice.entity.Inventory;
import com.orderflow.inventoryservice.entity.Product;
import com.orderflow.inventoryservice.kafka.event.OrderCreatedEvent;
import com.orderflow.inventoryservice.repository.InventoryRepository;
import com.orderflow.inventoryservice.repository.InventoryReservationRepository;
import com.orderflow.inventoryservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private ProductRepository productRepository;

    private InventoryReservationService reservationService;

    private static final Long ORDER_ID = 100L;

    @BeforeEach
    void setUp() {
        reservationService = new InventoryReservationService(inventoryRepository, reservationRepository, productRepository);
    }

    @Test
    void reserve_allItemsHaveSufficientStock_decrementsEachAndSavesReservations() {
        Inventory inv1 = new Inventory(1L, 10);
        Inventory inv2 = new Inventory(2L, 5);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(inv2));

        List<OrderCreatedEvent.Item> items = List.of(
                new OrderCreatedEvent.Item(1L, 3, 1000L),
                new OrderCreatedEvent.Item(2L, 2, 500L));

        ReservationOutcome outcome = reservationService.reserve(ORDER_ID, items);

        assertThat(outcome.success()).isTrue();
        assertThat(inv1.getQuantityAvailable()).isEqualTo(7);
        assertThat(inv2.getQuantityAvailable()).isEqualTo(3);
        verify(reservationRepository).save(argThat(r -> r.getOrderId().equals(ORDER_ID)
                && r.getProductId().equals(1L) && r.getQuantity().equals(3)));
        verify(reservationRepository).save(argThat(r -> r.getOrderId().equals(ORDER_ID)
                && r.getProductId().equals(2L) && r.getQuantity().equals(2)));
    }

    @Test
    void reserve_oneItemInsufficientStock_decrementsNothing_andSavesNoReservations() {
        Inventory inv1 = new Inventory(1L, 10);
        Inventory inv2 = new Inventory(2L, 1); // insufficient for requested quantity 2
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(inv2));
        when(productRepository.findById(2L)).thenReturn(Optional.of(new Product("SKU-2", "Gadget", 500L)));

        List<OrderCreatedEvent.Item> items = List.of(
                new OrderCreatedEvent.Item(1L, 3, 1000L),
                new OrderCreatedEvent.Item(2L, 2, 500L));

        ReservationOutcome outcome = reservationService.reserve(ORDER_ID, items);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.failureReason()).contains("SKU-2");
        // all-or-nothing: neither item's inventory is touched, even the one with enough stock
        assertThat(inv1.getQuantityAvailable()).isEqualTo(10);
        assertThat(inv2.getQuantityAvailable()).isEqualTo(1);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_missingInventoryRow_treatedAsInsufficientStock_failsWithoutThrowing() {
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.empty());

        List<OrderCreatedEvent.Item> items = List.of(new OrderCreatedEvent.Item(1L, 1, 1000L));

        ReservationOutcome outcome = reservationService.reserve(ORDER_ID, items);

        assertThat(outcome.success()).isFalse();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_sameProductOnTwoLines_checksCombinedRequirement() {
        // available=5, two lines of 3 each => combined requirement 6 > 5, must fail as a whole
        Inventory inv = new Inventory(1L, 5);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(productRepository.findById(1L)).thenReturn(Optional.of(new Product("SKU-1", "Widget", 1000L)));

        List<OrderCreatedEvent.Item> items = List.of(
                new OrderCreatedEvent.Item(1L, 3, 1000L),
                new OrderCreatedEvent.Item(1L, 3, 1000L));

        ReservationOutcome outcome = reservationService.reserve(ORDER_ID, items);

        assertThat(outcome.success()).isFalse();
        assertThat(inv.getQuantityAvailable()).isEqualTo(5);
    }
}
