package com.orderflow.paymentservice.kafka;

import com.orderflow.paymentservice.kafka.event.OrderCreatedEvent;
import com.orderflow.paymentservice.service.PaymentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final PaymentProcessingService paymentProcessingService;

    public OrderCreatedListener(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    @KafkaListener(topics = "order.created", containerFactory = "orderCreatedContainerFactory")
    public void onOrderCreated(OrderCreatedEvent event) {
        MDC.put("correlationId", String.valueOf(event.correlationId()));
        try {
            log.info("Received order.created for orderId={}", event.orderId());
            paymentProcessingService.upsertPendingCharge(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
