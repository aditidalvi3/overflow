package com.orderflow.orderservice.service;

import com.orderflow.orderservice.client.InventoryClient;
import com.orderflow.orderservice.dto.OrderItemRequest;
import com.orderflow.orderservice.dto.OrderResponse;
import com.orderflow.orderservice.dto.PlaceOrderRequest;
import com.orderflow.orderservice.dto.ProductDto;
import com.orderflow.orderservice.entity.Order;
import com.orderflow.orderservice.entity.OrderItem;
import com.orderflow.orderservice.entity.OrderStatus;
import com.orderflow.orderservice.exception.BadRequestException;
import com.orderflow.orderservice.exception.ConflictException;
import com.orderflow.orderservice.exception.NotFoundException;
import com.orderflow.orderservice.kafka.OrderEventPublisher;
import com.orderflow.orderservice.kafka.event.OrderCreatedItem;
import com.orderflow.orderservice.repository.OrderItemRepository;
import com.orderflow.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private IdempotencyService idempotencyService;

    private OrderService orderService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderItemRepository, inventoryClient,
                eventPublisher, idempotencyService, 24L);
    }

    @Test
    void placeOrder_missingIdempotencyKey_throwsBadRequest() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), null);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, null, request))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(inventoryClient, orderRepository, eventPublisher);
    }

    @Test
    void placeOrder_blankIdempotencyKey_throwsBadRequest() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), null);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, "  ", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void placeOrder_idempotencyKeySeenBefore_looksUpCurrentOrderStateFromDb_doesNotRepriceOrRepublish() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), null);
        String cacheKey = "idempotency:order:" + USER_ID + ":key-1";
        when(idempotencyService.findOrderId(cacheKey)).thenReturn(Optional.of("99"));

        Order existingOrder = new Order(USER_ID, 2000L, OrderStatus.PENDING, "corr-1");
        setId(existingOrder, 99L);
        when(orderRepository.findById(99L)).thenReturn(Optional.of(existingOrder));
        when(orderItemRepository.findByOrderId(99L)).thenReturn(List.of());

        OrderResponse result = orderService.placeOrder(USER_ID, "key-1", request);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(inventoryClient, eventPublisher);
        verify(orderRepository, never()).save(any());
        verify(idempotencyService, never()).saveOrderId(anyString(), anyString(), any());
    }

    @Test
    void placeOrder_productNotFound_throwsNotFound_doesNotCreateOrderOrPublish() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), null);
        when(idempotencyService.findOrderId(anyString())).thenReturn(Optional.empty());
        when(inventoryClient.getProduct(1L)).thenThrow(new NotFoundException("Product not found: 1"));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, "key-2", request))
                .isInstanceOf(NotFoundException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verify(idempotencyService, never()).saveOrderId(anyString(), anyString(), any());
    }

    @Test
    void placeOrder_happyPath_pricesItemsFromInventoryService_persistsPendingOrder_publishesOrderCreated_andCachesOrderId() {
        PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(2L, 1)), "tok_123");
        when(idempotencyService.findOrderId(anyString())).thenReturn(Optional.empty());

        when(inventoryClient.getProduct(1L)).thenReturn(new ProductDto(1L, "SKU-1", "Widget", 1000L, 50));
        when(inventoryClient.getProduct(2L)).thenReturn(new ProductDto(2L, "SKU-2", "Gadget", 500L, 20));

        Order savedOrder = new Order(USER_ID, 2500L, OrderStatus.PENDING, "ignored");
        setId(savedOrder, 42L);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem item = inv.getArgument(0);
            setId(item, 7L);
            return item;
        });

        OrderResponse response = orderService.placeOrder(USER_ID, "key-3", request);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.totalCents()).isEqualTo(2500L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(2);

        ArgumentCaptor<List<OrderCreatedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publishOrderCreated(eq(savedOrder), anyString(), eq("tok_123"), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).extracting(OrderCreatedItem::productId).containsExactly(1L, 2L);

        verify(idempotencyService).saveOrderId(eq("idempotency:order:" + USER_ID + ":key-3"), eq("42"), any(Duration.class));
    }

    @Test
    void cancelOrder_notPaid_throwsConflict_doesNotPublish() {
        Order order = new Order(USER_ID, 1000L, OrderStatus.PENDING, "corr-2");
        setId(order, 10L);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 10L))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelOrder_paid_setsCancelled_andPublishesOrderCancelledWithOriginalCorrelationId() {
        Order order = new Order(USER_ID, 1000L, OrderStatus.PAID, "corr-3");
        setId(order, 11L);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(11L)).thenReturn(List.of());

        OrderResponse response = orderService.cancelOrder(USER_ID, 11L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher).publishOrderCancelled(order, "corr-3");
    }

    @Test
    void cancelOrder_notOwnedByUser_throwsNotFound() {
        Order order = new Order(2L, 1000L, OrderStatus.PAID, "corr-4");
        setId(order, 12L);
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 12L))
                .isInstanceOf(NotFoundException.class);
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
