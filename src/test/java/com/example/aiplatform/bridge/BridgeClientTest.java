package com.example.aiplatform.bridge;

import com.example.aiplatform.bridge.dto.BridgeGenerateImageRequest;
import com.example.aiplatform.bridge.dto.BridgeGenerateImageResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

/**
 * 使用 MockWebServer 模拟 Python Bridge HTTP 服务。
 */
public class BridgeClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void generateImage_ok() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"requestId\":\"r1\",\"status\":\"DONE\",\"imageUrl\":\"http://x\"}"));

        BridgeClientConfig cfg = new BridgeClientConfig();
        // 反射设置 private 字段（避免引入 Spring Context）
        TestReflect.set(cfg, "baseUrl", server.url("/").toString().replaceAll("/$", ""));
        TestReflect.set(cfg, "requestTimeoutSeconds", 3L);
        TestReflect.set(cfg, "maxAttempts", 1);
        TestReflect.set(cfg, "backoffMillis", 10L);

        BridgeClient client = new BridgeClient(WebClient.create(), cfg);

        BridgeGenerateImageRequest req = new BridgeGenerateImageRequest();
        req.setRequestId("r1");
        req.setPrompt("cat");

        BridgeGenerateImageResponse resp = client.generateImage(req);
        Assertions.assertEquals("r1", resp.getRequestId());
        Assertions.assertEquals("DONE", resp.getStatus());
    }

    @Test
    void generateImage_5xx_should_retry_then_success() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"requestId\":\"r2\",\"status\":\"DONE\"}"));

        BridgeClientConfig cfg = new BridgeClientConfig();
        TestReflect.set(cfg, "baseUrl", server.url("/").toString().replaceAll("/$", ""));
        TestReflect.set(cfg, "requestTimeoutSeconds", 3L);
        TestReflect.set(cfg, "maxAttempts", 2);
        TestReflect.set(cfg, "backoffMillis", 1L);

        BridgeClient client = new BridgeClient(WebClient.create(), cfg);

        BridgeGenerateImageRequest req = new BridgeGenerateImageRequest();
        req.setRequestId("r2");
        req.setPrompt("cat");

        BridgeGenerateImageResponse resp = client.generateImage(req);
        Assertions.assertEquals("DONE", resp.getStatus());
    }

    @Test
    void generateImage_4xx_should_not_retry_and_fail() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("bad"));

        BridgeClientConfig cfg = new BridgeClientConfig();
        TestReflect.set(cfg, "baseUrl", server.url("/").toString().replaceAll("/$", ""));
        TestReflect.set(cfg, "requestTimeoutSeconds", 3L);
        TestReflect.set(cfg, "maxAttempts", 3);
        TestReflect.set(cfg, "backoffMillis", 1L);

        BridgeClient client = new BridgeClient(WebClient.create(), cfg);

        BridgeGenerateImageRequest req = new BridgeGenerateImageRequest();
        req.setRequestId("r3");
        req.setPrompt("cat");

        Assertions.assertThrows(BridgeException.class, () -> client.generateImage(req));
    }

    /** 简单反射工具 */
    static class TestReflect {
        static void set(Object obj, String field, Object val) {
            try {
                var f = obj.getClass().getDeclaredField(field);
                f.setAccessible(true);
                f.set(obj, val);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
