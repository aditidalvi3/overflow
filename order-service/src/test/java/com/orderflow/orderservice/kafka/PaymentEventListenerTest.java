package com.orderflow.orderservice.kafka;

import com.orderflow.orderservice.entity.Order;
import com.orderflow.orderservice.entity.OrderStatus;
import com.orderflow.orderservice.entity.ProcessedEvent;
import com.orderflow.orderservice.kafka.event.PaymentFailedEvent;
import com.orderflow.orderservice.kafka.event.PaymentProcessedEvent;
import com.orderflow.orderservice.repository.OrderRepository;
import com.orderflow.orderservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private OrderEventPublisher eventPublisher;

    private PaymentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentEventListener(orderRepository, processedEventRepository, eventPublisher);
    }

    @Test
    void onPaymentProcessed_marksOrderPaid_andPublishesOrderConfirmed() {
        Order order = new Order(1L, 2000L, OrderStatus.PENDING, "corr-1");
        setId(order, 60L);
        when(orderRepository.findById(60L)).thenReturn(Optional.of(order));

        var event = new PaymentProcessedEvent("evt-1", "corr-1", 60L, 900L, 2000L, "prov_ref", Instant.now());

        listener.onPaymentProcessed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(processedEventRepository).saveAndFlush(argThat(pe ->
                pe.getTopic().equals(Topics.PAYMENT_PROCESSED) && pe.getOrderId().equals(60L)));
        verify(eventPublisher).publishOrderConfirmed(order, "corr-1");
    }

    @Test
    void onPaymentProcessed_duplicateDelivery_skipsBusinessLogic() {
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var event = new PaymentProcessedEvent("evt-2", "corr-2", 60L, 900L, 2000L, "prov_ref", Instant.now());

        listener.onPaymentProcessed(event);

        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void onPaymentFailed_marksOrderPaymentFailed_andPublishesOrderFailedWithReason() {
        Order order = new Order(1L, 2000L, OrderStatus.PENDING, "corr-3");
        setId(order, 61L);
        when(orderRepository.findById(61L)).thenReturn(Optional.of(order));

        var event = new PaymentFailedEvent("evt-3", "corr-3", 61L, "FAILED", "Card declined", Instant.now());

        listener.onPaymentFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(eventPublisher).publishOrderFailed(order, "corr-3", "Card declined");
    }

    @Test
    void onPaymentFailed_duplicateDelivery_skipsBusinessLogic() {
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var event = new PaymentFailedEvent("evt-4", "corr-4", 61L, "TIMEOUT", "Gateway timeout", Instant.now());

        listener.onPaymentFailed(event);

        verifyNoInteractions(orderRepository, eventPublisher);
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
