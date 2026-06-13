package com.example.aiplatform.service;

import com.example.aiplatform.entity.User;
import com.example.aiplatform.mapper.UserMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserService.JwtUtilFacade jwtFacade;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private UserService userService;

    @Test
    void register_ok() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return 1;
        });

        User u = userService.register("alice", "Passw0rd!");
        Assertions.assertNotNull(u);
        Assertions.assertEquals("alice", u.getUsername());
        Assertions.assertTrue(passwordEncoder.matches("Passw0rd!", u.getPasswordHash()));
    }

    @Test
    void register_duplicate_username_fail() {
        User exist = new User();
        exist.setId(9L);
        exist.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(exist);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.register("alice", "Passw0rd!"));
    }

    @Test
    void login_wrong_password_fail() {
        User u = new User();
        u.setId(1L);
        u.setUsername("alice");
        u.setStatus(1);
        u.setPasswordHash(passwordEncoder.encode("RightPass"));

        when(userMapper.selectOne(any())).thenReturn(u);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.loginAndIssueToken("alice", "WrongPass"));
    }

    @Test
    void login_ok_should_write_token_to_redis() {
        User u = new User();
        u.setId(100L);
        u.setUsername("alice");
        u.setStatus(1);
        u.setPasswordHash(passwordEncoder.encode("Passw0rd!"));

        when(userMapper.selectOne(any())).thenReturn(u);

        when(jwtFacade.newJti()).thenReturn("jti-1");
        when(jwtFacade.generateToken(eq(100L), eq("alice"), eq("jti-1"))).thenReturn("jwt-token");
        when(jwtFacade.getExpireSeconds()).thenReturn(60L);
        when(jwtFacade.isSsoEnabled()).thenReturn(true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:sso:100")).thenReturn(null);

        String token = userService.loginAndIssueToken("alice", "Passw0rd!");
        Assertions.assertEquals("jwt-token", token);

        // tokenKey 写入
        verify(valueOperations, times(1))
                .set(eq("auth:token:100:jti-1"), eq("1"), eq(Duration.ofSeconds(60)));

        // ssoKey 写入
        verify(valueOperations, times(1))
                .set(eq("auth:sso:100"), eq("jti-1"), eq(Duration.ofSeconds(60)));

        // 不应删除旧 tokenKey（因为 oldJti=null）
        verify(redisTemplate, never()).delete(startsWith("auth:token:100:"));
    }
}
