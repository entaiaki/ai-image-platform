package com.example.aiplatform.task;

import com.example.aiplatform.bridge.BridgeClient;
import com.example.aiplatform.bridge.dto.BridgeGenerateImageRequest;
import com.example.aiplatform.bridge.dto.BridgeGenerateImageResponse;
import com.example.aiplatform.entity.ImageTask;
import com.example.aiplatform.service.ImageTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Redis Worker：异步闭环
 * - Producer: 提交 taskId 到 Redis List
 * - Consumer: BRPOP 拉取 taskId
 * - Spring Worker 调用 Python Bridge (/generate-image)
 * - 回写 DB 状态：PENDING -> PROCESSING -> DONE/FAILED
 * - 失败重试 3 次（ZSET 延迟队列），超过进入 DLQ
 */
@Component
public class TaskConsumer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TaskConsumer.class);

    private static final int MAX_RETRIES = 3;

    private final StringRedisTemplate redisTemplate;
    private final TaskProducer producer;
    private final ImageTaskService imageTaskService;
    private final BridgeClient bridgeClient;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("redis-task-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = true;

    public TaskConsumer(StringRedisTemplate redisTemplate,
                        TaskProducer producer,
                        ImageTaskService imageTaskService,
                        BridgeClient bridgeClient) {
        this.redisTemplate = redisTemplate;
        this.producer = producer;
        this.imageTaskService = imageTaskService;
        this.bridgeClient = bridgeClient;
    }

    @Override
    public void afterPropertiesSet() {
        executor.submit(this::loop);
    }

    private void loop() {
        while (running) {
            String taskIdStr = null;
            Long taskId = null;
            try {
                // 阻塞式消费：BRPOP key timeout
                taskIdStr = redisTemplate.opsForList()
                        .rightPop(TaskProducer.QUEUE_KEY, Duration.ofSeconds(5));

                if (taskIdStr == null || taskIdStr.isBlank()) {
                    continue;
                }

                taskId = Long.parseLong(taskIdStr);

                // PENDING/FAILED -> PROCESSING
                imageTaskService.markProcessing(taskId);

                ImageTask t = imageTaskService.getById(taskId);
                if (t == null) {
                    log.warn("Task not found in db, drop. taskId={}", taskId);
                    continue;
                }

                BridgeGenerateImageRequest req = new BridgeGenerateImageRequest();
                req.setRequestId(t.getRequestId());
                req.setPrompt(t.getPrompt());
                req.setNegativePrompt(t.getNegativePrompt());

                BridgeGenerateImageResponse resp = bridgeClient.generateImage(req);

                // DONE: 要求保存 image_path（优先 localPath；再尝试 image_paths[0]；没有则退化用 imageUrl）
                String imagePath = resp == null ? null : resp.getLocalPath();
                if (imagePath == null || imagePath.isBlank()) {
                    if (resp != null && resp.getImagePaths() != null && !resp.getImagePaths().isEmpty()) {
                        imagePath = resp.getImagePaths().get(0);
                    }
                }
                if (imagePath == null || imagePath.isBlank()) {
                    imagePath = resp == null ? null : resp.getImageUrl();
                }

                imageTaskService.markDone(taskId, resp == null ? null : resp.getImageUrl(), imagePath);

            } catch (Exception e) {
                // 失败：重试/死信
                if (taskId != null) {
                    String reason = e.getMessage() == null ? e.toString() : e.getMessage();
                    log.warn("Task failed taskId={}, will retry/dlq. err={}", taskId, reason);
                    onTaskFailed(taskId, reason);
                } else {
                    log.warn("Worker loop error before parsing taskId. raw={}, err={}", taskIdStr, e.toString());
                    sleepQuietly(500);
                }
            }
        }
    }

    /**
     * 扫描延迟重试 ZSET，把到期任务重新投递回主队列。
     */
    @Scheduled(fixedDelay = 2000)
    public void retryDueTasks() {
        long now = System.currentTimeMillis();
        Set<String> due = redisTemplate.opsForZSet().rangeByScore(TaskProducer.RETRY_ZSET_KEY, 0, now, 0, 200);
        if (due == null || due.isEmpty()) return;

        for (String taskIdStr : due) {
            Long removed = redisTemplate.opsForZSet().remove(TaskProducer.RETRY_ZSET_KEY, taskIdStr);
            if (removed == null || removed == 0) continue;
            redisTemplate.opsForList().leftPush(TaskProducer.QUEUE_KEY, taskIdStr);
        }
    }

    /**
     * 失败重试：最多 3 次。超过进入 DLQ。
     * 说明：retry_count 计数写在 DB（当前项目最小闭环，不额外加组件）。
     */
    public void onTaskFailed(Long taskId, String reason) {
        ImageTask t = imageTaskService.getById(taskId);
        if (t == null) return;

        int retryCount = t.getRetryCount() == null ? 0 : t.getRetryCount();
        retryCount++;

        // 直接更新 retry_count 字段（最小修改：走 mapper updateById）
        t.setRetryCount(retryCount);
        // 用 service 的失败状态回写（若状态不允许会抛异常，但不影响重试调度）
        try {
            imageTaskService.markFailed(taskId, reason);
        } catch (Exception ignore) {
        }
        // 再补一次 updateById，确保 retry_count 落库（markFailed 内部只更新 status/fail_reason）
        try {
            imageTaskService.updateRetryCount(taskId, retryCount);
        } catch (Exception ignore) {
        }

        if (retryCount <= MAX_RETRIES) {
            long delaySeconds = (long) Math.pow(2, retryCount); // 2,4,8
            producer.scheduleRetry(taskId, delaySeconds);
        } else {
            producer.toDeadLetter(taskId, reason);
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        running = false;
        executor.shutdownNow();
    }
}
