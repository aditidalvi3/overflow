package com.orderflow.inventoryservice.service;

import com.orderflow.inventoryservice.entity.Inventory;
import com.orderflow.inventoryservice.entity.InventoryReservation;
import com.orderflow.inventoryservice.repository.InventoryRepository;
import com.orderflow.inventoryservice.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReleaseServiceTest {

    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryReleaseService releaseService;

    private static final Long ORDER_ID = 200L;

    @BeforeEach
    void setUp() {
        releaseService = new InventoryReleaseService(reservationRepository, inventoryRepository);
    }

    @Test
    void release_paymentFailedCompensation_increasesInventoryForEachReservedItem() {
        InventoryReservation r1 = new InventoryReservation(ORDER_ID, 1L, 3);
        InventoryReservation r2 = new InventoryReservation(ORDER_ID, 2L, 2);
        Inventory inv1 = new Inventory(1L, 7);
        Inventory inv2 = new Inventory(2L, 3);

        when(reservationRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(r1, r2));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(inv2));

        releaseService.release(ORDER_ID);

        assertThat(inv1.getQuantityAvailable()).isEqualTo(10);
        assertThat(inv2.getQuantityAvailable()).isEqualTo(5);
    }

    @Test
    void release_orderCancelledCompensation_sameReleaseLogicAppliesForCancelledPaidOrder() {
        InventoryReservation r1 = new InventoryReservation(ORDER_ID, 5L, 4);
        Inventory inv1 = new Inventory(5L, 1);

        when(reservationRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(r1));
        when(inventoryRepository.findByProductIdForUpdate(5L)).thenReturn(Optional.of(inv1));

        releaseService.release(ORDER_ID);

        assertThat(inv1.getQuantityAvailable()).isEqualTo(5);
    }

    @Test
    void release_noReservationsFoundForOrder_isNoOp_doesNotThrow() {
        when(reservationRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        releaseService.release(ORDER_ID);
        // no interactions with inventoryRepository expected beyond none - implicit via no stubbing needed
    }
}
