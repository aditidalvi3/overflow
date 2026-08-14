package com.orderflow.orderservice.security;

import com.orderflow.orderservice.entity.Role;

public record UserPrincipal(Long userId, String email, Role role) {
}
