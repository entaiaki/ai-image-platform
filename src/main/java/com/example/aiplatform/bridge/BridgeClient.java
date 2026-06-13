package com.example.aiplatform.bridge;

import com.example.aiplatform.bridge.dto.BridgeGenerateImageRequest;
import com.example.aiplatform.bridge.dto.BridgeGenerateImageResponse;
import com.example.aiplatform.bridge.util.StopWatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class BridgeClient {

    private static final Logger log = LoggerFactory.getLogger(BridgeClient.class);

    private final WebClient webClient;
    private final BridgeClientConfig config;

    public BridgeClient(WebClient webClient, BridgeClientConfig config) {
        this.webClient = webClient;
        this.config = config;
    }

    /**
     * 同步调用 Bridge 生成图片。
     * - timeout: 使用 Mono.timeout
     * - retry: 手写 for-loop 重试（避免引入 reactor-retry 的复杂度，日志更可控）
     */
    public BridgeGenerateImageResponse generateImage(BridgeGenerateImageRequest req) {
        if (req == null || req.getRequestId() == null || req.getRequestId().isBlank()) {
            throw new IllegalArgumentException("requestId required");
        }
        if (req.getPrompt() == null || req.getPrompt().isBlank()) {
            throw new IllegalArgumentException("prompt required");
        }

        String url = config.getBaseUrl() + "/generate-image";
        long start = StopWatchUtil.now();

        int maxAttempts = Math.max(1, config.getMaxAttempts());
        long backoff = Math.max(100, config.getBackoffMillis());

        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("BridgeClient.generateImage start requestId={}, attempt={}/{}", req.getRequestId(), attempt, maxAttempts);

                BridgeGenerateImageResponse resp = webClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .bodyValue(req)
                        .retrieve()
                        .bodyToMono(BridgeGenerateImageResponse.class)
                        .timeout(config.getRequestTimeout())
                        .onErrorResume(e -> Mono.error(mapToBridgeException(req.getRequestId(), e)))
                        .block();

                long cost = StopWatchUtil.costMs(start);
                log.info("BridgeClient.generateImage success requestId={}, costMs={}, response={}", req.getRequestId(), cost, resp);
                return resp;

            } catch (Exception e) {
                last = e;
                long cost = StopWatchUtil.costMs(start);

                boolean retryable = isRetryable(e);
                log.warn("BridgeClient.generateImage failed requestId={}, attempt={}/{}, costMs={}, retryable={}, err={}",
                        req.getRequestId(), attempt, maxAttempts, cost, retryable, e.toString());

                if (!retryable || attempt == maxAttempts) {
                    if (e instanceof BridgeException be) throw be;
                    throw new BridgeException(req.getRequestId(), "Bridge call failed: " + e.getMessage(), e);
                }

                sleepQuietly(backoff * attempt);
            }
        }

        throw new BridgeException(req.getRequestId(), "Bridge call failed (unknown)", last);
    }

    private boolean isRetryable(Throwable e) {
        Throwable root = unwrap(e);

        // 4xx 不重试，5xx 重试
        if (root instanceof WebClientResponseException wcre) {
            int code = wcre.getStatusCode().value();
            return code >= 500;
        }

        // 连接/IO/超时重试
        return (root instanceof WebClientRequestException)
                || (root instanceof java.util.concurrent.TimeoutException);
    }

    private Throwable unwrap(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur != cur.getCause()) {
            cur = cur.getCause();
        }
        return cur;
    }

    private RuntimeException mapToBridgeException(String requestId, Throwable e) {
        Throwable root = unwrap(e);

        if (root instanceof WebClientResponseException wcre) {
            String body = wcre.getResponseBodyAsString();
            return new BridgeException(requestId,
                    "Bridge HTTP " + wcre.getStatusCode().value() + " " + wcre.getStatusText() + ", body=" + body, wcre);
        }
        if (root instanceof WebClientRequestException) {
            return new BridgeException(requestId, "Bridge network error: " + root.getMessage(), root);
        }
        if (root instanceof java.util.concurrent.TimeoutException) {
            return new BridgeException(requestId, "Bridge timeout", root);
        }
        return new BridgeException(requestId, "Bridge unexpected error: " + root.getMessage(), root);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
