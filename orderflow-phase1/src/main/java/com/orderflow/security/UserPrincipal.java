package com.orderflow.security;

import com.orderflow.entity.Role;

public record UserPrincipal(Long userId, String email, Role role) {
}
