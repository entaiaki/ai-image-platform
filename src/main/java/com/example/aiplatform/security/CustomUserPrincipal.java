package com.example.aiplatform.security;

public class CustomUserPrincipal {

    private final Long userId;
    private final String username;
    private final String jti;

    public CustomUserPrincipal(Long userId, String username, String jti) {
        this.userId = userId;
        this.username = username;
        this.jti = jti;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getJti() {
        return jti;
    }
}
