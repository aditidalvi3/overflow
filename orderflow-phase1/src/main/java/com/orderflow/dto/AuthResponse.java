package com.orderflow.dto;

public record AuthResponse(String token, Long userId, String email) {
}
