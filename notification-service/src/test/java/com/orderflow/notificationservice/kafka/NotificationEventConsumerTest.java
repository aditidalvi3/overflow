package com.orderflow.notificationservice.kafka;

import com.orderflow.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(notificationService);
    }

    @Test
    void onOrderConfirmed_delegatesToNotificationService() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), 1L, 2L, Instant.now());

        consumer.onOrderConfirmed(event);

        verify(notificationService).handleOrderConfirmed(event);
    }

    @Test
    void onOrderFailed_delegatesToNotificationService() {
        OrderFailedEvent event = new OrderFailedEvent(UUID.randomUUID(), UUID.randomUUID(), 1L, 2L, "reason", Instant.now());

        consumer.onOrderFailed(event);

        verify(notificationService).handleOrderFailed(event);
    }
}
