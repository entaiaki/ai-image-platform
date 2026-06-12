package com.example.aiplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiImagePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiImagePlatformApplication.class, args);
    }
}
