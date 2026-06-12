package com.example.aiplatform.ws.dto;

import com.example.aiplatform.task.TaskStatus;

import java.time.LocalDateTime;

public class TaskStatusEvent {
    private Long taskId;
    private Long userId;
    private TaskStatus status;
    private String message;
    private LocalDateTime time;

    public static TaskStatusEvent of(Long taskId, Long userId, TaskStatus status, String message) {
        TaskStatusEvent e = new TaskStatusEvent();
        e.taskId = taskId;
        e.userId = userId;
        e.status = status;
        e.message = message;
        e.time = LocalDateTime.now();
        return e;
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTime() { return time; }
    public void setTime(LocalDateTime time) { this.time = time; }
}
