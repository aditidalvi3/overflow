package com.orderflow.notificationservice.kafka;

import com.orderflow.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final NotificationService notificationService;

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order.confirmed", containerFactory = "orderConfirmedKafkaListenerContainerFactory")
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        MDC.put(MDC_CORRELATION_ID, String.valueOf(event.correlationId()));
        try {
            log.info("Received order.confirmed for orderId={}", event.orderId());
            notificationService.handleOrderConfirmed(event);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    @KafkaListener(topics = "order.failed", containerFactory = "orderFailedKafkaListenerContainerFactory")
    public void onOrderFailed(OrderFailedEvent event) {
        MDC.put(MDC_CORRELATION_ID, String.valueOf(event.correlationId()));
        try {
            log.info("Received order.failed for orderId={}", event.orderId());
            notificationService.handleOrderFailed(event);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
