package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.kafka.event.OrderCreatedEvent;
import com.orderflow.inventoryservice.service.InventoryReservationService;
import com.orderflow.inventoryservice.service.ReservationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreatedListenerTest {

    @Mock
    private ProcessedEventGuard processedEventGuard;
    @Mock
    private InventoryReservationService reservationService;
    @Mock
    private InventoryEventPublisher publisher;

    private OrderCreatedListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderCreatedListener(processedEventGuard, reservationService, publisher);
    }

    private OrderCreatedEvent sampleEvent(Long orderId) {
        return new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), orderId, 1L, 2000L, null,
                List.of(new OrderCreatedEvent.Item(1L, 2, 1000L)), Instant.now());
    }

    @Test
    void onMessage_duplicateDelivery_isSkipped_reservationLogicNeverRuns() {
        OrderCreatedEvent event = sampleEvent(1L);
        when(processedEventGuard.isDuplicate(KafkaTopics.ORDER_CREATED, 1L)).thenReturn(true);

        listener.onMessage(event);

        verifyNoInteractions(reservationService, publisher);
    }

    @Test
    void onMessage_freshEvent_reservationSucceeds_publishesReserved() {
        OrderCreatedEvent event = sampleEvent(2L);
        when(processedEventGuard.isDuplicate(KafkaTopics.ORDER_CREATED, 2L)).thenReturn(false);
        when(reservationService.reserve(2L, event.items())).thenReturn(ReservationOutcome.succeeded());

        listener.onMessage(event);

        verify(publisher).publishReserved(event.correlationId(), 2L);
        verify(publisher, never()).publishReservationFailed(any(), any(), any());
    }

    @Test
    void onMessage_freshEvent_reservationFails_publishesReservationFailedWithReason() {
        OrderCreatedEvent event = sampleEvent(3L);
        when(processedEventGuard.isDuplicate(KafkaTopics.ORDER_CREATED, 3L)).thenReturn(false);
        when(reservationService.reserve(3L, event.items()))
                .thenReturn(ReservationOutcome.failure("Insufficient stock for SKU SKU-1: requested 2, available 0"));

        listener.onMessage(event);

        verify(publisher).publishReservationFailed(event.correlationId(), 3L,
                "Insufficient stock for SKU SKU-1: requested 2, available 0");
        verify(publisher, never()).publishReserved(any(), any());
    }
}
