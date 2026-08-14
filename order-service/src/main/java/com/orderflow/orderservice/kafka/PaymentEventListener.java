package com.orderflow.orderservice.kafka;

import com.orderflow.orderservice.entity.Order;
import com.orderflow.orderservice.entity.ProcessedEvent;
import com.orderflow.orderservice.exception.NotFoundException;
import com.orderflow.orderservice.kafka.event.PaymentFailedEvent;
import com.orderflow.orderservice.kafka.event.PaymentProcessedEvent;
import com.orderflow.orderservice.repository.OrderRepository;
import com.orderflow.orderservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private static final String MDC_KEY = "correlationId";

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderEventPublisher eventPublisher;

    public PaymentEventListener(OrderRepository orderRepository,
                                 ProcessedEventRepository processedEventRepository,
                                 OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = Topics.PAYMENT_PROCESSED)
    @Transactional
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        MDC.put(MDC_KEY, event.correlationId());
        try {
            if (alreadyProcessed(Topics.PAYMENT_PROCESSED, event.orderId())) {
                return;
            }
            Order order = orderRepository.findById(event.orderId())
                    .orElseThrow(() -> new NotFoundException("Order not found: " + event.orderId()));
            order.markPaid();
            eventPublisher.publishOrderConfirmed(order, event.correlationId());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        MDC.put(MDC_KEY, event.correlationId());
        try {
            if (alreadyProcessed(Topics.PAYMENT_FAILED, event.orderId())) {
                return;
            }
            Order order = orderRepository.findById(event.orderId())
                    .orElseThrow(() -> new NotFoundException("Order not found: " + event.orderId()));
            order.markPaymentFailed();
            eventPublisher.publishOrderFailed(order, event.correlationId(), event.reason());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private boolean alreadyProcessed(String topic, Long orderId) {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(topic, orderId));
            return false;
        } catch (DataIntegrityViolationException ex) {
            log.info("duplicate delivery, skipping: topic={} orderId={}", topic, orderId);
            return true;
        }
    }
}
