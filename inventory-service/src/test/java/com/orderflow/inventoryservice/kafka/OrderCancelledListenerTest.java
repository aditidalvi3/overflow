package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.kafka.event.OrderCancelledEvent;
import com.orderflow.inventoryservice.service.InventoryReleaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelledListenerTest {

    @Mock
    private ProcessedEventGuard processedEventGuard;
    @Mock
    private InventoryReleaseService releaseService;
    @Mock
    private InventoryEventPublisher publisher;

    private OrderCancelledListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderCancelledListener(processedEventGuard, releaseService, publisher);
    }

    @Test
    void onMessage_duplicateDelivery_isSkipped_releaseLogicNeverRuns() {
        OrderCancelledEvent event = new OrderCancelledEvent(UUID.randomUUID(), UUID.randomUUID(), 20L, Instant.now());
        when(processedEventGuard.isDuplicate(KafkaTopics.ORDER_CANCELLED, 20L)).thenReturn(true);

        listener.onMessage(event);

        verifyNoInteractions(releaseService, publisher);
    }

    @Test
    void onMessage_freshEvent_releasesReservedStock_andPublishesReleased() {
        OrderCancelledEvent event = new OrderCancelledEvent(UUID.randomUUID(), UUID.randomUUID(), 21L, Instant.now());
        when(processedEventGuard.isDuplicate(KafkaTopics.ORDER_CANCELLED, 21L)).thenReturn(false);

        listener.onMessage(event);

        verify(releaseService).release(21L);
        verify(publisher).publishReleased(event.correlationId(), 21L);
    }
}
