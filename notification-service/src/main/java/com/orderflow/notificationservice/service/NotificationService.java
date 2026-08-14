package com.orderflow.notificationservice.service;

import com.orderflow.notificationservice.entity.NotificationLog;
import com.orderflow.notificationservice.entity.NotificationType;
import com.orderflow.notificationservice.entity.ProcessedEvent;
import com.orderflow.notificationservice.kafka.OrderConfirmedEvent;
import com.orderflow.notificationservice.kafka.OrderFailedEvent;
import com.orderflow.notificationservice.notification.NotificationSender;
import com.orderflow.notificationservice.repository.NotificationLogRepository;
import com.orderflow.notificationservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String TOPIC_ORDER_CONFIRMED = "order.confirmed";
    private static final String TOPIC_ORDER_FAILED = "order.failed";
    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String PLACEHOLDER_RECIPIENT = "user@example.com";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSender notificationSender;

    public NotificationService(ProcessedEventRepository processedEventRepository,
                                NotificationLogRepository notificationLogRepository,
                                NotificationSender notificationSender) {
        this.processedEventRepository = processedEventRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.notificationSender = notificationSender;
    }

    @Transactional
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        if (!tryMarkProcessed(TOPIC_ORDER_CONFIRMED, event.orderId())) {
            return;
        }
        String subject = "Order Confirmed";
        String body = "Your order #" + event.orderId() + " has been confirmed. Thank you for shopping with OrderFlow!";
        notificationSender.send(PLACEHOLDER_RECIPIENT, subject, body);
        notificationLogRepository.save(new NotificationLog(event.orderId(), NotificationType.ORDER_CONFIRMED,
                CHANNEL_EMAIL, PLACEHOLDER_RECIPIENT, body));
    }

    @Transactional
    public void handleOrderFailed(OrderFailedEvent event) {
        if (!tryMarkProcessed(TOPIC_ORDER_FAILED, event.orderId())) {
            return;
        }
        String subject = "Order Failed";
        String body = "Your order #" + event.orderId() + " could not be completed. Reason: " + event.reason();
        notificationSender.send(PLACEHOLDER_RECIPIENT, subject, body);
        notificationLogRepository.save(new NotificationLog(event.orderId(), NotificationType.ORDER_FAILED,
                CHANNEL_EMAIL, PLACEHOLDER_RECIPIENT, body));
    }

    /**
     * Idempotent-consumer guard from the shared contract: insert into processed_events first,
     * inside the same transaction as the business logic. A unique-constraint violation means
     * this (topic, orderId) pair was already handled by a prior delivery of the same message.
     */
    private boolean tryMarkProcessed(String topic, Long orderId) {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(topic, orderId));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("duplicate delivery, skipping topic={} orderId={}", topic, orderId);
            return false;
        }
    }
}
