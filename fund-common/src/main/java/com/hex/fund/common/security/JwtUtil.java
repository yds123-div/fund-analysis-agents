package com.hex.fund.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类，基于 HMAC-SHA256 签名。纯工具类，不依赖 Spring 容器。
 */
public final class JwtUtil {

    private static final long EXPIRE_MS = 24 * 60 * 60 * 1000L;

    private JwtUtil() {
    }

    /**
     * 生成 Token，包含 userId / username / role
     */
    public static String generateToken(String secret, Long userId, String username, String role) {
        SecretKey key = toKey(secret);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + EXPIRE_MS))
                .signWith(key)
                .compact();
    }

    /**
     * 验证并解析 Token，返回 Claims；无效时抛出 JwtException
     */
    public static Claims parseToken(String secret, String token) {
        return Jwts.parser()
                .verifyWith(toKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 刷新 Token（基于旧 Token 的 Claims 重新签发）
     */
    public static String refreshToken(String secret, String token) {
        Claims claims = parseToken(secret, token);
        Long userId = Long.valueOf(claims.getSubject());
        return generateToken(secret, userId,
                claims.get("username", String.class),
                claims.get("role", String.class));
    }

    private static SecretKey toKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}
