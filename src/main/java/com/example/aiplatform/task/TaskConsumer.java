package com.example.aiplatform.task;

import com.example.aiplatform.entity.ImageTask;
import com.example.aiplatform.service.ImageTaskService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Redis Worker：
 * 1) 从 image:task:queue (List) 拉取任务
 * 2) 处理失败重试 3 次（延迟重试队列 ZSET）
 * 3) 超过次数进入死信队列 image:task:dlq
 *
 * 注意：当前阶段仅做"Worker消费"框架，真正调用 Python Bridge 在下一阶段接入。
 */
@Component
@EnableScheduling
public class TaskConsumer implements InitializingBean, DisposableBean {

    private static final int MAX_RETRIES = 3;

    private final StringRedisTemplate redisTemplate;
    private final TaskProducer producer;
    private final ImageTaskService imageTaskService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("redis-task-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = true;

    public TaskConsumer(StringRedisTemplate redisTemplate,
                        TaskProducer producer,
                        ImageTaskService imageTaskService) {
        this.redisTemplate = redisTemplate;
        this.producer = producer;
        this.imageTaskService = imageTaskService;
    }

    @Override
    public void afterPropertiesSet() {
        executor.submit(this::loop);
    }

    private void loop() {
        while (running) {
            try {
                // 阻塞式消费：BRPOP key timeout
                // Spring Data Redis: rightPop(key, timeout)
                String taskIdStr = redisTemplate.opsForList()
                        .rightPop(TaskProducer.QUEUE_KEY, Duration.ofSeconds(5));

                if (taskIdStr == null || taskIdStr.isBlank()) {
                    continue;
                }

                Long taskId = Long.parseLong(taskIdStr);

                // 标记 processing（状态机校验在 service 内）
                imageTaskService.markProcessing(taskId);

                // TODO: 下一阶段在这里调用 Python Bridge / ComfyUI
                // 当前阶段先模拟成功（实际接入后改为 DONE/FAILED）
                // imageTaskService.markDone(taskId, "", "");

                // 这里为了演示重试机制，默认不自动 DONE：让调用方或下一阶段接入来更新

            } catch (Exception e) {
                // 如果在处理时抛异常：尝试重试
                // 注意：这里拿不到 taskId（除非更精细的 try/catch），所以使用较保守策略：忽略。
                // 企业级实现应在每个任务处理块内 catch 并处理重试。
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
            // 先从 ZSET 移除，避免重复投递
            Long removed = redisTemplate.opsForZSet().remove(TaskProducer.RETRY_ZSET_KEY, taskIdStr);
            if (removed == null || removed == 0) continue;

            redisTemplate.opsForList().leftPush(TaskProducer.QUEUE_KEY, taskIdStr);
        }
    }

    /**
     * 对外提供：当 Python Bridge 调用失败时，触发重试。
     * 当前阶段先写好：按 image_tasks.retry_count 计数。
     */
    public void onTaskFailed(Long taskId, String reason) {
        ImageTask t = imageTaskService.getById(taskId);
        if (t == null) return;

        int retryCount = t.getRetryCount() == null ? 0 : t.getRetryCount();
        retryCount++;
        t.setRetryCount(retryCount);

        if (retryCount <= MAX_RETRIES) {
            // 标记 FAILED（也可以保持 PROCESSING，取决于你的审计策略；这里选择 FAILED + 延迟重试）
            try {
                imageTaskService.markFailed(taskId, reason);
            } catch (Exception ignore) {
                // 如果状态不允许 markFailed（例如还没 PROCESSING），也不要阻塞重试调度
            }

            // 指数退避：2, 4, 8 秒
            long delaySeconds = (long) Math.pow(2, retryCount);
            producer.scheduleRetry(taskId, delaySeconds);
        } else {
            // 入死信队列
            try {
                imageTaskService.markFailed(taskId, "DLQ: " + reason);
            } catch (Exception ignore) {
            }
            producer.toDeadLetter(taskId, reason);
        }
    }

    @Override
    public void destroy() {
        running = false;
        executor.shutdownNow();
    }
}
