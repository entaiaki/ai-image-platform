package com.example.aiplatform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final String issuer;
    private final long expireSeconds;

    public JwtUtil(@Value("${app.security.jwt.secret}") String secret,
                   @Value("${app.security.jwt.issuer}") String issuer,
                   @Value("${app.security.jwt.expire-seconds}") long expireSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expireSeconds = expireSeconds;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public String generateToken(Long userId, String username, String jti) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .id(jti)
                .claim("username", username)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public String newJti() {
        return UUID.randomUUID().toString();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
