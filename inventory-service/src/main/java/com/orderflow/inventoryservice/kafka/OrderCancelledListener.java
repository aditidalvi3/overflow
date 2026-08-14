package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.kafka.event.OrderCancelledEvent;
import com.orderflow.inventoryservice.service.InventoryReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledListener.class);

    private final ProcessedEventGuard processedEventGuard;
    private final InventoryReleaseService releaseService;
    private final InventoryEventPublisher publisher;

    public OrderCancelledListener(ProcessedEventGuard processedEventGuard,
                                   InventoryReleaseService releaseService,
                                   InventoryEventPublisher publisher) {
        this.processedEventGuard = processedEventGuard;
        this.releaseService = releaseService;
        this.publisher = publisher;
    }

    @Transactional
    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, containerFactory = "orderCancelledContainerFactory")
    public void onMessage(OrderCancelledEvent event) {
        MDC.put("correlationId", String.valueOf(event.correlationId()));
        try {
            if (processedEventGuard.isDuplicate(KafkaTopics.ORDER_CANCELLED, event.orderId())) {
                log.info("duplicate delivery, skipping: topic={} orderId={}", KafkaTopics.ORDER_CANCELLED, event.orderId());
                return;
            }

            releaseService.release(event.orderId());
            publisher.publishReleased(event.correlationId(), event.orderId());
        } finally {
            MDC.remove("correlationId");
        }
    }
}
