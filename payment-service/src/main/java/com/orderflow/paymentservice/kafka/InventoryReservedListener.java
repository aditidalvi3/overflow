package com.orderflow.paymentservice.kafka;

import com.orderflow.paymentservice.kafka.event.InventoryReservedEvent;
import com.orderflow.paymentservice.service.PaymentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryReservedListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservedListener.class);

    private final PaymentProcessingService paymentProcessingService;

    public InventoryReservedListener(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    @KafkaListener(topics = "inventory.reserved", containerFactory = "inventoryReservedContainerFactory")
    public void onInventoryReserved(InventoryReservedEvent event) {
        MDC.put("correlationId", String.valueOf(event.correlationId()));
        try {
            log.info("Received inventory.reserved for orderId={}", event.orderId());
            paymentProcessingService.processCharge(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
