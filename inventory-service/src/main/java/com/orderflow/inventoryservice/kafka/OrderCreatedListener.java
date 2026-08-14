package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.kafka.event.OrderCreatedEvent;
import com.orderflow.inventoryservice.service.InventoryReservationService;
import com.orderflow.inventoryservice.service.ReservationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final ProcessedEventGuard processedEventGuard;
    private final InventoryReservationService reservationService;
    private final InventoryEventPublisher publisher;

    public OrderCreatedListener(ProcessedEventGuard processedEventGuard,
                                 InventoryReservationService reservationService,
                                 InventoryEventPublisher publisher) {
        this.processedEventGuard = processedEventGuard;
        this.reservationService = reservationService;
        this.publisher = publisher;
    }

    @Transactional
    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, containerFactory = "orderCreatedContainerFactory")
    public void onMessage(OrderCreatedEvent event) {
        MDC.put("correlationId", String.valueOf(event.correlationId()));
        try {
            if (processedEventGuard.isDuplicate(KafkaTopics.ORDER_CREATED, event.orderId())) {
                log.info("duplicate delivery, skipping: topic={} orderId={}", KafkaTopics.ORDER_CREATED, event.orderId());
                return;
            }

            ReservationOutcome outcome = reservationService.reserve(event.orderId(), event.items());
            if (outcome.success()) {
                publisher.publishReserved(event.correlationId(), event.orderId());
            } else {
                publisher.publishReservationFailed(event.correlationId(), event.orderId(), outcome.failureReason());
            }
        } finally {
            MDC.remove("correlationId");
        }
    }
}
