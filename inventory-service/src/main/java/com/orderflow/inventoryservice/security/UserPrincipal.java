package com.orderflow.inventoryservice.security;

import com.orderflow.inventoryservice.entity.Role;

public record UserPrincipal(Long userId, String email, Role role) {
}
