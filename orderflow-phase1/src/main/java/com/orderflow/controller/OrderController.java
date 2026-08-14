package com.orderflow.controller;

import com.orderflow.dto.OrderResponse;
import com.orderflow.dto.PlaceOrderRequest;
import com.orderflow.security.UserPrincipal;
import com.orderflow.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                      @Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse response = orderService.placeOrder(principal.userId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> listOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orderService.listOrders(principal.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(principal.userId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(principal.userId(), id));
    }
}
