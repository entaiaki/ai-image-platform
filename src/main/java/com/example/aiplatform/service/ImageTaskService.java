package com.example.aiplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiplatform.entity.ImageTask;
import com.example.aiplatform.mapper.ImageTaskMapper;
import com.example.aiplatform.task.TaskProducer;
import com.example.aiplatform.task.TaskStatus;
import com.example.aiplatform.ws.TaskStatusBroadcaster;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ImageTaskService {

    private final ImageTaskMapper imageTaskMapper;
    private final TaskStatusBroadcaster broadcaster;
    private final TaskProducer producer;

    public ImageTaskService(ImageTaskMapper imageTaskMapper,
                            TaskStatusBroadcaster broadcaster,
                            TaskProducer producer) {
        this.imageTaskMapper = imageTaskMapper;
        this.broadcaster = broadcaster;
        this.producer = producer;
    }

    public ImageTask createTask(Long userId, String prompt, String negativePrompt) {
        ImageTask t = new ImageTask();
        t.setUserId(userId);
        t.setRequestId(UUID.randomUUID().toString().replace("-", ""));
        t.setPrompt(prompt);
        t.setNegativePrompt(negativePrompt);
        t.setStatus(TaskStatus.PENDING.name());
        t.setRetryCount(0);
        t.setMaxRetries(3);
        imageTaskMapper.insert(t);
        broadcaster.publish(t.getId(), userId, TaskStatus.PENDING, "Task created");
        return t;
    }

    public ImageTask getById(Long id) {
        return imageTaskMapper.selectById(id);
    }

    /**
     * submit() 闭环：创建任务 + 入队
     */
    public ImageTask createTaskAndEnqueue(Long userId, String prompt, String negativePrompt) {
        ImageTask t = createTask(userId, prompt, negativePrompt);
        producer.submit(t.getId());
        return t;
    }

    /**
     * 最小闭环需要：失败重试时更新 retry_count（不引入额外模块）。
     */
    public void updateRetryCount(Long taskId, int retryCount) {
        ImageTask t = mustGet(taskId);
        t.setRetryCount(retryCount);
        imageTaskMapper.updateById(t);
    }

    public List<ImageTask> listByUser(Long userId, int limit) {
        return imageTaskMapper.selectList(new LambdaQueryWrapper<ImageTask>()
                .eq(ImageTask::getUserId, userId)
                .orderByDesc(ImageTask::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    /**
     * 状态机流转：
     * PENDING -> PROCESSING -> DONE
     * PROCESSING -> FAILED
     * FAILED 允许 -> PROCESSING (重试) 由后续模块实现
     */
    public void markProcessing(Long taskId) {
        ImageTask t = mustGet(taskId);
        if (!TaskStatus.PENDING.name().equals(t.getStatus()) && !TaskStatus.FAILED.name().equals(t.getStatus())) {
            throw new IllegalStateException("Invalid status transition to PROCESSING from " + t.getStatus());
        }
        t.setStatus(TaskStatus.PROCESSING.name());
        t.setFailReason(null);
        imageTaskMapper.updateById(t);
        broadcaster.publish(t.getId(), t.getUserId(), TaskStatus.PROCESSING, "Task processing");
    }

    public void markDone(Long taskId, String outputImageUrl, String outputLocalPath) {
        ImageTask t = mustGet(taskId);
        if (!TaskStatus.PROCESSING.name().equals(t.getStatus())) {
            throw new IllegalStateException("Invalid status transition to DONE from " + t.getStatus());
        }
        t.setStatus(TaskStatus.DONE.name());
        t.setOutputImageUrl(outputImageUrl);
        t.setOutputLocalPath(outputLocalPath);
        t.setFailReason(null);
        imageTaskMapper.updateById(t);
        broadcaster.publish(t.getId(), t.getUserId(), TaskStatus.DONE, "Task done");
    }

    public void markFailed(Long taskId, String reason) {
        ImageTask t = mustGet(taskId);
        if (!TaskStatus.PROCESSING.name().equals(t.getStatus())) {
            throw new IllegalStateException("Invalid status transition to FAILED from " + t.getStatus());
        }
        t.setStatus(TaskStatus.FAILED.name());
        t.setFailReason(reason);
        imageTaskMapper.updateById(t);
        broadcaster.publish(t.getId(), t.getUserId(), TaskStatus.FAILED, reason);
    }

    private ImageTask mustGet(Long taskId) {
        ImageTask t = imageTaskMapper.selectById(taskId);
        if (t == null) throw new IllegalArgumentException("Task not found");
        return t;
    }
}
