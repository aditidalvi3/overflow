package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.kafka.event.PaymentFailedEvent;
import com.orderflow.inventoryservice.service.InventoryReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentFailedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedListener.class);

    private final ProcessedEventGuard processedEventGuard;
    private final InventoryReleaseService releaseService;
    private final InventoryEventPublisher publisher;

    public PaymentFailedListener(ProcessedEventGuard processedEventGuard,
                                  InventoryReleaseService releaseService,
                                  InventoryEventPublisher publisher) {
        this.processedEventGuard = processedEventGuard;
        this.releaseService = releaseService;
        this.publisher = publisher;
    }

    @Transactional
    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, containerFactory = "paymentFailedContainerFactory")
    public void onMessage(PaymentFailedEvent event) {
        MDC.put("correlationId", String.valueOf(event.correlationId()));
        try {
            if (processedEventGuard.isDuplicate(KafkaTopics.PAYMENT_FAILED, event.orderId())) {
                log.info("duplicate delivery, skipping: topic={} orderId={}", KafkaTopics.PAYMENT_FAILED, event.orderId());
                return;
            }

            releaseService.release(event.orderId());
            publisher.publishReleased(event.correlationId(), event.orderId());
        } finally {
            MDC.remove("correlationId");
        }
    }
}
