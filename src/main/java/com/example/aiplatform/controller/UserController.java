package com.example.aiplatform.controller;

import com.example.aiplatform.common.Result;
import com.example.aiplatform.dto.LoginDTO;
import com.example.aiplatform.dto.RegisterDTO;
import com.example.aiplatform.security.JwtUtil;
import com.example.aiplatform.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto.getUsername(), dto.getPassword());
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        String token = userService.loginAndIssueToken(dto.getUsername(), dto.getPassword());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("expireSeconds", jwtUtil.getExpireSeconds());
        return Result.ok(data);
    }

    /**
     * 登出：从 Redis 删除 tokenKey。
     * 这里直接从 Authorization 解析 token，避免依赖 SecurityContext（更直观）。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.fail(400, "Missing Bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Claims claims = jwtUtil.parse(token);
        Long userId = Long.parseLong(claims.getSubject());
        String jti = claims.getId();
        userService.logout(userId, jti);
        return Result.ok();
    }
}
