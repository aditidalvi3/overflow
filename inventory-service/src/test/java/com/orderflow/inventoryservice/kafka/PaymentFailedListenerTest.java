package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.kafka.event.PaymentFailedEvent;
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
class PaymentFailedListenerTest {

    @Mock
    private ProcessedEventGuard processedEventGuard;
    @Mock
    private InventoryReleaseService releaseService;
    @Mock
    private InventoryEventPublisher publisher;

    private PaymentFailedListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentFailedListener(processedEventGuard, releaseService, publisher);
    }

    @Test
    void onMessage_duplicateDelivery_isSkipped_releaseLogicNeverRuns() {
        PaymentFailedEvent event = new PaymentFailedEvent(UUID.randomUUID(), UUID.randomUUID(), 10L,
                "FAILED", "Card declined", Instant.now());
        when(processedEventGuard.isDuplicate(KafkaTopics.PAYMENT_FAILED, 10L)).thenReturn(true);

        listener.onMessage(event);

        verifyNoInteractions(releaseService, publisher);
    }

    @Test
    void onMessage_freshEvent_releasesReservedStock_andPublishesReleased() {
        PaymentFailedEvent event = new PaymentFailedEvent(UUID.randomUUID(), UUID.randomUUID(), 11L,
                "FAILED", "Card declined", Instant.now());
        when(processedEventGuard.isDuplicate(KafkaTopics.PAYMENT_FAILED, 11L)).thenReturn(false);

        listener.onMessage(event);

        verify(releaseService).release(11L);
        verify(publisher).publishReleased(event.correlationId(), 11L);
    }
}
