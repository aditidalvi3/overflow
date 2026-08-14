package com.orderflow.service;

import com.orderflow.dto.OrderItemRequest;
import com.orderflow.dto.OrderItemResponse;
import com.orderflow.dto.OrderResponse;
import com.orderflow.dto.PlaceOrderRequest;
import com.orderflow.entity.Inventory;
import com.orderflow.entity.Order;
import com.orderflow.entity.OrderItem;
import com.orderflow.entity.OrderStatus;
import com.orderflow.entity.Payment;
import com.orderflow.entity.PaymentStatus;
import com.orderflow.entity.Product;
import com.orderflow.exception.BadRequestException;
import com.orderflow.exception.ConflictException;
import com.orderflow.exception.NotFoundException;
import com.orderflow.repository.InventoryRepository;
import com.orderflow.repository.OrderItemRepository;
import com.orderflow.repository.OrderRepository;
import com.orderflow.repository.PaymentRepository;
import com.orderflow.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final MockPaymentGateway paymentGateway;
    private final IdempotencyService idempotencyService;
    private final long idempotencyTtlHours;

    public OrderService(OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         InventoryRepository inventoryRepository,
                         ProductRepository productRepository,
                         PaymentRepository paymentRepository,
                         MockPaymentGateway paymentGateway,
                         IdempotencyService idempotencyService,
                         @Value("${orderflow.idempotency.ttl-hours}") long idempotencyTtlHours) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.idempotencyService = idempotencyService;
        this.idempotencyTtlHours = idempotencyTtlHours;
    }

    @Transactional
    public OrderResponse placeOrder(Long userId, String idempotencyKey, PlaceOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        String cacheKey = "idempotency:order:" + userId + ":" + idempotencyKey;
        var cached = idempotencyService.find(cacheKey, OrderResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        long totalCents = 0;
        List<OrderItem> pendingItems = new ArrayList<>();
        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + itemRequest.productId()));
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(itemRequest.productId())
                    .orElseThrow(() -> new NotFoundException("Inventory not found for product: " + itemRequest.productId()));

            if (inventory.getQuantityAvailable() < itemRequest.quantity()) {
                throw new ConflictException("Insufficient stock for product " + product.getSku());
            }
            inventory.decrease(itemRequest.quantity());

            totalCents += product.getPriceCents() * itemRequest.quantity();
            pendingItems.add(new OrderItem(null, product.getId(), itemRequest.quantity(), product.getPriceCents()));
        }

        PaymentOutcome outcome = paymentGateway.charge(totalCents, request.paymentToken());

        OrderStatus finalStatus;
        if (outcome.status() == PaymentStatus.SUCCEEDED) {
            finalStatus = OrderStatus.PAID;
        } else {
            // Compensate: release the stock we reserved above, since the charge didn't go through.
            for (OrderItem pending : pendingItems) {
                inventoryRepository.findByProductIdForUpdate(pending.getProductId())
                        .ifPresent(inv -> inv.increase(pending.getQuantity()));
            }
            finalStatus = OrderStatus.PAYMENT_FAILED;
        }

        Order order = orderRepository.save(new Order(userId, totalCents, finalStatus));

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem pending : pendingItems) {
            OrderItem saved = orderItemRepository.save(
                    new OrderItem(order.getId(), pending.getProductId(), pending.getQuantity(), pending.getUnitPriceCents()));
            itemResponses.add(new OrderItemResponse(saved.getProductId(), saved.getQuantity(), saved.getUnitPriceCents()));
        }

        paymentRepository.save(new Payment(order.getId(), totalCents, outcome.status(),
                outcome.providerRef(), outcome.failureReason()));

        OrderResponse response = new OrderResponse(order.getId(), order.getStatus(), order.getTotalCents(),
                order.getCreatedAt(), itemResponses, outcome.status(), outcome.failureReason());
        idempotencyService.save(cacheKey, response, Duration.ofHours(idempotencyTtlHours));
        return response;
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

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new NotFoundException("Inventory not found for product: " + item.getProductId()));
            inventory.increase(item.getQuantity());
        }

        order.cancel();
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getQuantity(), i.getUnitPriceCents()))
                .toList();
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);
        PaymentStatus paymentStatus = payment == null ? null : payment.getStatus();
        String paymentFailureReason = payment == null ? null : payment.getFailureReason();
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalCents(), order.getCreatedAt(),
                items, paymentStatus, paymentFailureReason);
    }
}
