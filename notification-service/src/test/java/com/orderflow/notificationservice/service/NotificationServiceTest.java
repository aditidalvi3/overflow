package com.orderflow.notificationservice.service;

import com.orderflow.notificationservice.entity.NotificationLog;
import com.orderflow.notificationservice.entity.NotificationType;
import com.orderflow.notificationservice.entity.ProcessedEvent;
import com.orderflow.notificationservice.kafka.OrderConfirmedEvent;
import com.orderflow.notificationservice.kafka.OrderFailedEvent;
import com.orderflow.notificationservice.notification.NotificationSender;
import com.orderflow.notificationservice.repository.NotificationLogRepository;
import com.orderflow.notificationservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private NotificationSender notificationSender;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(processedEventRepository, notificationLogRepository, notificationSender);
    }

    @Test
    void handleOrderConfirmed_sendsNotification_andPersistsLog() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), 42L, 7L, Instant.now());
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handleOrderConfirmed(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).send(anyString(), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo("Order Confirmed");
        assertThat(bodyCaptor.getValue()).contains("42");

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        NotificationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getOrderId()).isEqualTo(42L);
        assertThat(savedLog.getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(savedLog.getChannel()).isEqualTo("EMAIL");
        assertThat(savedLog.getRecipient()).isNotBlank();
        assertThat(savedLog.getContent()).contains("42");
    }

    @Test
    void handleOrderConfirmed_duplicateDelivery_skipsProcessing() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), 42L, 7L, Instant.now());
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        notificationService.handleOrderConfirmed(event);

        verifyNoInteractions(notificationSender);
        verifyNoInteractions(notificationLogRepository);
    }

    @Test
    void handleOrderFailed_sendsNotification_withReason_andPersistsLog() {
        OrderFailedEvent event = new OrderFailedEvent(UUID.randomUUID(), UUID.randomUUID(), 43L, 7L, "Card declined", Instant.now());
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handleOrderFailed(event);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).send(anyString(), eq("Order Failed"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("Card declined").contains("43");

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getType()).isEqualTo(NotificationType.ORDER_FAILED);
        assertThat(logCaptor.getValue().getOrderId()).isEqualTo(43L);
    }

    @Test
    void handleOrderFailed_duplicateDelivery_skipsProcessing() {
        OrderFailedEvent event = new OrderFailedEvent(UUID.randomUUID(), UUID.randomUUID(), 43L, 7L, "Card declined", Instant.now());
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        notificationService.handleOrderFailed(event);

        verifyNoInteractions(notificationSender);
        verifyNoInteractions(notificationLogRepository);
    }
}
