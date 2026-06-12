package com.example.aiplatform.bridge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BridgeClientConfig {

    @Value("${app.bridge.base-url:http://127.0.0.1:7861}")
    private String baseUrl;

    @Value("${app.bridge.request-timeout-seconds:60}")
    private long requestTimeoutSeconds;

    @Value("${app.bridge.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.bridge.retry.backoff-millis:500}")
    private long backoffMillis;

    public String getBaseUrl() {
        return baseUrl;
    }

    public Duration getRequestTimeout() {
        return Duration.ofSeconds(requestTimeoutSeconds);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getBackoffMillis() {
        return backoffMillis;
    }
}
