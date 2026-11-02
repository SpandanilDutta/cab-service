package com.cabservice.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens are random opaque strings (not JWTs).
 * Only a SHA-256 hash of the token is stored in Redis, so a Redis leak does not leak
 * usable tokens. Redis TTL removes expired tokens automatically.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenStore(StringRedisTemplate redis,
                             @Value("${cabservice.auth.jwt.refresh-token-days}") long refreshTokenDays) {
        this.redis = redis;
        this.ttl = Duration.ofDays(refreshTokenDays);
    }

    /** Creates a new refresh token for the user and returns the raw token (shown to the client once). */
    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(KEY_PREFIX + hash(token), userId.toString(), ttl);
        return token;
    }

    /**
     * Single use: returns the owner and deletes the token in ONE atomic Redis command (GETDEL).
     * If two requests use the same token at the same instant, only one of them gets the user id.
     */
    public Optional<UUID> consume(String token) {
        String userId = redis.opsForValue().getAndDelete(KEY_PREFIX + hash(token));
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    public void revoke(String token) {
        redis.delete(KEY_PREFIX + hash(token));
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}