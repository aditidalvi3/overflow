package com.orderflow.orderservice.service;

import com.orderflow.orderservice.client.InventoryClient;
import com.orderflow.orderservice.dto.OrderItemRequest;
import com.orderflow.orderservice.dto.OrderItemResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryClient inventoryClient;
    private final OrderEventPublisher eventPublisher;
    private final IdempotencyService idempotencyService;
    private final long idempotencyTtlHours;

    public OrderService(OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         InventoryClient inventoryClient,
                         OrderEventPublisher eventPublisher,
                         IdempotencyService idempotencyService,
                         @Value("${orderflow.idempotency.ttl-hours}") long idempotencyTtlHours) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryClient = inventoryClient;
        this.eventPublisher = eventPublisher;
        this.idempotencyService = idempotencyService;
        this.idempotencyTtlHours = idempotencyTtlHours;
    }

    /**
     * Order creation is asynchronous: this method only prices the requested items, persists the
     * order as PENDING, and kicks off the saga by publishing order.created. Clients must poll
     * GET /api/orders/{id} to observe the saga's eventual resolution (PAID / INVENTORY_FAILED /
     * PAYMENT_FAILED).
     */
    @Transactional
    public OrderResponse placeOrder(Long userId, String idempotencyKey, PlaceOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        String cacheKey = "idempotency:order:" + userId + ":" + idempotencyKey;
        var cachedOrderId = idempotencyService.findOrderId(cacheKey);
        if (cachedOrderId.isPresent()) {
            return getOrder(userId, Long.valueOf(cachedOrderId.get()));
        }

        long totalCents = 0;
        List<PricedItem> pricedItems = new ArrayList<>();
        for (OrderItemRequest itemRequest : request.items()) {
            ProductDto product = inventoryClient.getProduct(itemRequest.productId());
            totalCents += product.priceCents() * itemRequest.quantity();
            pricedItems.add(new PricedItem(product.id(), itemRequest.quantity(), product.priceCents()));
        }

        String correlationId = UUID.randomUUID().toString();
        Order order = orderRepository.save(new Order(userId, totalCents, OrderStatus.PENDING, correlationId));

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        List<OrderCreatedItem> eventItems = new ArrayList<>();
        for (PricedItem pi : pricedItems) {
            OrderItem saved = orderItemRepository.save(
                    new OrderItem(order.getId(), pi.productId(), pi.quantity(), pi.unitPriceCents()));
            itemResponses.add(new OrderItemResponse(saved.getProductId(), saved.getQuantity(), saved.getUnitPriceCents()));
            eventItems.add(new OrderCreatedItem(saved.getProductId(), saved.getQuantity(), saved.getUnitPriceCents()));
        }

        eventPublisher.publishOrderCreated(order, correlationId, request.paymentToken(), eventItems);
        idempotencyService.saveOrderId(cacheKey, String.valueOf(order.getId()), Duration.ofHours(idempotencyTtlHours));

        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalCents(), order.getCreatedAt(), itemResponses);
    }

    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        return toResponse(order);
    }

    public List<OrderResponse> listOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("Only PAID orders can be cancelled");
        }

        order.cancel();
        eventPublisher.publishOrderCancelled(order, order.getCorrelationId());
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getQuantity(), i.getUnitPriceCents()))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalCents(), order.getCreatedAt(), items);
    }

    private record PricedItem(Long productId, Integer quantity, Long unitPriceCents) {
    }
}
