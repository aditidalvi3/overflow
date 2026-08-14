package com.orderflow.inventoryservice.security;

import com.orderflow.inventoryservice.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * inventory-service never issues tokens (only order-service owns the User table and does
 * that) - this is verification-only, hence no generateToken()/expiration handling here,
 * unlike Phase 1's JwtUtil which both issues and parses.
 */
@Component
public class JwtUtil {

    private final SecretKey key;

    public JwtUtil(@Value("${orderflow.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    public UserPrincipal parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        Role role = Role.valueOf(claims.get("role", String.class));
        return new UserPrincipal(userId, email, role);
    }
}
