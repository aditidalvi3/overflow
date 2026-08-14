package com.orderflow.orderservice.dto;

public record AuthResponse(String token, Long userId, String email) {
}
