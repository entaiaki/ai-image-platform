package com.example.aiplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterDTO {

    @NotBlank(message = "username required")
    @Size(min = 3, max = 32, message = "username length 3~32")
    private String username;

    @NotBlank(message = "password required")
    @Size(min = 6, max = 64, message = "password length 6~64")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
