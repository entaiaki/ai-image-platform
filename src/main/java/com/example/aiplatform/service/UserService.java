package com.example.aiplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiplatform.entity.User;
import com.example.aiplatform.mapper.UserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtilFacade jwtUtilFacade;
    private final StringRedisTemplate redisTemplate;

    public UserService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtilFacade jwtUtilFacade,
                       StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtilFacade = jwtUtilFacade;
        this.redisTemplate = redisTemplate;
    }

    public User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    public User register(String username, String rawPassword) {
        User exist = findByUsername(username);
        if (exist != null) {
            throw new IllegalArgumentException("Username already exists");
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setStatus(1);
        userMapper.insert(u);
        return u;
    }

    /**
     * 登录成功后签发 token，并把 token 写入 Redis 作为"会话"。
     */
    public String loginAndIssueToken(String username, String rawPassword) {
        User u = findByUsername(username);
        if (u == null) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw new IllegalStateException("User disabled");
        }
        if (!passwordEncoder.matches(rawPassword, u.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String jti = jwtUtilFacade.newJti();
        String token = jwtUtilFacade.generateToken(u.getId(), u.getUsername(), jti);

        // Redis: auth:token:{userId}:{jti} = 1
        String tokenKey = tokenKey(u.getId(), jti);
        redisTemplate.opsForValue().set(tokenKey, "1", Duration.ofSeconds(jwtUtilFacade.getExpireSeconds()));

        // SSO: auth:sso:{userId} = jti (覆盖旧的，并删除旧tokenKey)
        if (jwtUtilFacade.isSsoEnabled()) {
            String ssoKey = ssoKey(u.getId());
            String oldJti = redisTemplate.opsForValue().get(ssoKey);
            if (oldJti != null && !oldJti.isBlank()) {
                redisTemplate.delete(tokenKey(u.getId(), oldJti));
            }
            redisTemplate.opsForValue().set(ssoKey, jti, Duration.ofSeconds(jwtUtilFacade.getExpireSeconds()));
        }

        return token;
    }

    public boolean isTokenActive(Long userId, String jti) {
        Boolean exist = redisTemplate.hasKey(tokenKey(userId, jti));
        return exist != null && exist;
    }

    public void logout(Long userId, String jti) {
        redisTemplate.delete(tokenKey(userId, jti));
    }

    private String tokenKey(Long userId, String jti) {
        return "auth:token:" + userId + ":" + jti;
    }

    private String ssoKey(Long userId) {
        return "auth:sso:" + userId;
    }

    /**
     * 用于隔离 JwtUtil 与 app 配置，避免在 Service 层到处注入 @Value。
     */
    public interface JwtUtilFacade {
        String newJti();
        String generateToken(Long userId, String username, String jti);
        long getExpireSeconds();
        boolean isSsoEnabled();
    }
}
