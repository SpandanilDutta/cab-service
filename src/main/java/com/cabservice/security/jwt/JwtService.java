package com.cabservice.security.jwt;


import com.cabservice.common.util.AppConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and validates short-lived ACCESS tokens (signed JWT).
 * Validation needs no database query: the signature proves we issued the token.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenMinutes;

    public JwtService(@Value("${cabservice.auth.jwt.secret}") String secret,
                      @Value("${cabservice.auth.jwt.access-token-minutes}") long accessTokenMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("cabservice.auth.jwt.secret must be at least 32 characters (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String generateAccessToken(UUID userId, String username, Collection<AppConstant.Role> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roles.stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** @return the claims if the signature is valid and the token is not expired, otherwise empty */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenMinutes * 60;
    }
}