package com.orderflow.service;

import com.orderflow.dto.OrderItemRequest;
import com.orderflow.dto.OrderResponse;
import com.orderflow.dto.PlaceOrderRequest;
import com.orderflow.entity.Inventory;
import com.orderflow.entity.Order;
import com.orderflow.entity.OrderItem;
import com.orderflow.entity.OrderStatus;
import com.orderflow.entity.PaymentStatus;
import com.orderflow.entity.Product;
import com.orderflow.exception.BadRequestException;
import com.orderflow.exception.ConflictException;
import com.orderflow.repository.InventoryRepository;
import com.orderflow.repository.OrderItemRepository;
import com.orderflow.repository.OrderRepository;
import com.orderflow.repository.PaymentRepository;
import com.orderflow.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MockPaymentGateway paymentGateway;
    @Mock
    private IdempotencyService idempotencyService;

    private OrderService orderService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderItemRepository, inventoryRepository,
                productRepository, paymentRepository, paymentGateway, idempotencyService, 24L);
    }

    @Test
    void placeOrder_missingIdempotencyKey_throwsBadRequest() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), null);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, null, request))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(productRepository, inventoryRepository, orderRepository);
    }

    @Test
    void placeOrder_returnsCachedResponse_whenIdempotencyKeySeenBefore() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), null);
        OrderResponse cached = new OrderResponse(99L, OrderStatus.PAID, 500L, null, List.of(),
                PaymentStatus.SUCCEEDED, null);
        when(idempotencyService.find(anyString(), eq(OrderResponse.class))).thenReturn(Optional.of(cached));

        OrderResponse result = orderService.placeOrder(USER_ID, "key-1", request);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(productRepository, inventoryRepository, orderRepository);
    }

    @Test
    void placeOrder_insufficientStock_throwsConflict_andDoesNotCreateOrder() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 5)), null);
        when(idempotencyService.find(anyString(), eq(OrderResponse.class))).thenReturn(Optional.empty());

        Product product = new Product("SKU-1", "Widget", 1000L);
        setId(product, 1L);
        Inventory inventory = new Inventory(1L, 2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, "key-2", request))
                .isInstanceOf(ConflictException.class);

        verify(orderRepository, never()).save(any());
        verify(idempotencyService, never()).save(anyString(), any(), any());
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void placeOrder_happyPath_decrementsInventory_andCachesResponse() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), null);
        when(idempotencyService.find(anyString(), eq(OrderResponse.class))).thenReturn(Optional.empty());

        Product product = new Product("SKU-1", "Widget", 1000L);
        setId(product, 1L);
        Inventory inventory = new Inventory(1L, 10);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        when(paymentGateway.charge(2000L, null))
                .thenReturn(new PaymentOutcome(PaymentStatus.SUCCEEDED, "mock_ref", null));

        Order savedOrder = new Order(USER_ID, 2000L, OrderStatus.PAID);
        setId(savedOrder, 42L);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem item = inv.getArgument(0);
            setId(item, 7L);
            return item;
        });

        OrderResponse response = orderService.placeOrder(USER_ID, "key-3", request);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.totalCents()).isEqualTo(2000L);
        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(8);
        verify(idempotencyService).save(eq("idempotency:order:" + USER_ID + ":key-3"), eq(response), any());
        verify(paymentRepository).save(any());
    }

    @Test
    void placeOrder_paymentFails_releasesReservedInventory_andMarksOrderPaymentFailed() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), "tok_fail");
        when(idempotencyService.find(anyString(), eq(OrderResponse.class))).thenReturn(Optional.empty());

        Product product = new Product("SKU-1", "Widget", 1000L);
        setId(product, 1L);
        Inventory inventory = new Inventory(1L, 10);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        when(paymentGateway.charge(2000L, "tok_fail"))
                .thenReturn(new PaymentOutcome(PaymentStatus.FAILED, "mock_ref", "Card declined by issuing bank"));

        Order savedOrder = new Order(USER_ID, 2000L, OrderStatus.PAYMENT_FAILED);
        setId(savedOrder, 43L);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem item = inv.getArgument(0);
            setId(item, 8L);
            return item;
        });

        OrderResponse response = orderService.placeOrder(USER_ID, "key-4", request);

        assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.paymentFailureReason()).isEqualTo("Card declined by issuing bank");
        assertThat(inventory.getQuantityAvailable()).isEqualTo(10);
        verify(orderRepository).save(any());
        verify(idempotencyService).save(eq("idempotency:order:" + USER_ID + ":key-4"), eq(response), any());
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
