package com.orderflow.orderservice.kafka;

import com.orderflow.orderservice.entity.Order;
import com.orderflow.orderservice.entity.OrderStatus;
import com.orderflow.orderservice.entity.ProcessedEvent;
import com.orderflow.orderservice.exception.NotFoundException;
import com.orderflow.orderservice.kafka.event.InventoryReservationFailedEvent;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryEventListenerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private OrderEventPublisher eventPublisher;

    private InventoryEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new InventoryEventListener(orderRepository, processedEventRepository, eventPublisher);
    }

    @Test
    void onInventoryReservationFailed_marksOrderInventoryFailed_andPublishesOrderFailedWithSameCorrelationIdAndReason() {
        Order order = new Order(1L, 1000L, OrderStatus.PENDING, "corr-1");
        setId(order, 55L);
        when(orderRepository.findById(55L)).thenReturn(Optional.of(order));

        var event = new InventoryReservationFailedEvent(
                "evt-1", "corr-1", 55L, "Insufficient stock", Instant.now());

        listener.onInventoryReservationFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_FAILED);
        verify(processedEventRepository).saveAndFlush(argThat(pe ->
                pe.getTopic().equals(Topics.INVENTORY_RESERVATION_FAILED) && pe.getOrderId().equals(55L)));
        verify(eventPublisher).publishOrderFailed(order, "corr-1", "Insufficient stock");
    }

    @Test
    void onInventoryReservationFailed_duplicateDelivery_skipsBusinessLogic() {
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var event = new InventoryReservationFailedEvent(
                "evt-2", "corr-2", 55L, "Insufficient stock", Instant.now());

        listener.onInventoryReservationFailed(event);

        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void onInventoryReservationFailed_orderNotFound_throwsNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        var event = new InventoryReservationFailedEvent(
                "evt-3", "corr-3", 999L, "reason", Instant.now());

        assertThatThrownBy(() -> listener.onInventoryReservationFailed(event))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(eventPublisher);
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
