package com.example.aiplatform.task;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TaskProducer {

    public static final String QUEUE_KEY = "image:task:queue";
    public static final String RETRY_ZSET_KEY = "image:task:retry:zset";
    public static final String DLQ_KEY = "image:task:dlq";

    private final StringRedisTemplate redisTemplate;

    public TaskProducer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 提交任务：把 taskId 推入 Redis List 队列。
     */
    public void submit(Long taskId) {
        redisTemplate.opsForList().leftPush(QUEUE_KEY, String.valueOf(taskId));
    }

    /**
     * 延迟重试：将 taskId 放入 ZSET，score=下次可执行时间戳(ms)
     */
    public void scheduleRetry(Long taskId, long delaySeconds) {
        long nextTime = System.currentTimeMillis() + Duration.ofSeconds(delaySeconds).toMillis();
        redisTemplate.opsForZSet().add(RETRY_ZSET_KEY, String.valueOf(taskId), nextTime);
    }

    /**
     * 进入死信队列。
     */
    public void toDeadLetter(Long taskId, String reason) {
        // 简化：死信队列只存 taskId，原因可在 DB 的 fail_reason 字段里
        redisTemplate.opsForList().leftPush(DLQ_KEY, String.valueOf(taskId));
    }
}
