package com.orderflow.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private static final String SECRET =
            "ZmFrZS1kZXYtc2VjcmV0LWtleS1jaGFuZ2UtbWUtaW4tcHJvZHVjdGlvbi0xMjM0NTY=";

    private JwtValidator jwtValidator;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwtValidator = new JwtValidator(SECRET);
        key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
    }

    private String buildToken(Long userId, String role, Instant issuedAt, Instant expiration) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", "user@example.com")
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    @Test
    void validToken_isAccepted() {
        Instant now = Instant.now();
        String token = buildToken(42L, "CUSTOMER", now, now.plus(60, ChronoUnit.MINUTES));

        Optional<Claims> result = jwtValidator.validate(token);

        assertThat(result).isPresent();
        assertThat(result.get().getSubject()).isEqualTo("42");
        assertThat(result.get().get("role", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void expiredToken_isRejected() {
        Instant now = Instant.now();
        String token = buildToken(42L, "CUSTOMER", now.minus(120, ChronoUnit.MINUTES),
                now.minus(60, ChronoUnit.MINUTES));

        Optional<Claims> result = jwtValidator.validate(token);

        assertThat(result).isEmpty();
    }

    @Test
    void malformedToken_isRejected() {
        Optional<Claims> result = jwtValidator.validate("this.is-not.a-valid-jwt");

        assertThat(result).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentKey_isRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode("YW5vdGhlci1mYWtlLXNlY3JldC1rZXktZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="));
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("42")
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(60, ChronoUnit.MINUTES)))
                .signWith(otherKey)
                .compact();

        Optional<Claims> result = jwtValidator.validate(token);

        assertThat(result).isEmpty();
    }
}
