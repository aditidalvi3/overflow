package com.orderflow.paymentservice.kafka;

import com.orderflow.paymentservice.kafka.event.PaymentFailedEvent;
import com.orderflow.paymentservice.kafka.event.PaymentProcessedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final String TOPIC_PAYMENT_PROCESSED = "payment.processed";
    private static final String TOPIC_PAYMENT_FAILED = "payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        kafkaTemplate.send(TOPIC_PAYMENT_PROCESSED, String.valueOf(event.orderId()), event);
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(TOPIC_PAYMENT_FAILED, String.valueOf(event.orderId()), event);
    }
}
