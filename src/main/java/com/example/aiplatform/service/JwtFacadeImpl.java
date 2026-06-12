package com.example.aiplatform.service;

import com.example.aiplatform.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtFacadeImpl implements UserService.JwtUtilFacade {

    private final JwtUtil jwtUtil;
    private final boolean ssoEnabled;

    public JwtFacadeImpl(JwtUtil jwtUtil,
                         @Value("${app.security.jwt.sso-enabled:true}") boolean ssoEnabled) {
        this.jwtUtil = jwtUtil;
        this.ssoEnabled = ssoEnabled;
    }

    @Override
    public String newJti() {
        return jwtUtil.newJti();
    }

    @Override
    public String generateToken(Long userId, String username, String jti) {
        return jwtUtil.generateToken(userId, username, jti);
    }

    @Override
    public long getExpireSeconds() {
        return jwtUtil.getExpireSeconds();
    }

    @Override
    public boolean isSsoEnabled() {
        return ssoEnabled;
    }
}
