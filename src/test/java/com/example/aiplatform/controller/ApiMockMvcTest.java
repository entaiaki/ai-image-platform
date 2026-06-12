package com.example.aiplatform.controller;

import com.example.aiplatform.dto.LoginDTO;
import com.example.aiplatform.dto.RegisterDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_and_login_ok() throws Exception {
        String username = "u_" + UUID.randomUUID().toString().substring(0, 8);
        String password = "Passw0rd!";

        RegisterDTO reg = new RegisterDTO();
        reg.setUsername(username);
        reg.setPassword(password);

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        LoginDTO login = new LoginDTO();
        login.setUsername(username);
        login.setPassword(password);

        String resp = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(resp);
        String token = root.get("data").get("token").asText();
        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isBlank());
    }

    @Test
    void protected_api_without_token_should_401() throws Exception {
        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isUnauthorized());
    }
}
