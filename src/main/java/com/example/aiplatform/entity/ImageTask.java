package com.example.aiplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("image_tasks")
public class ImageTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String requestId;

    private String prompt;

    private String negativePrompt;

    /**
     * TaskStatus name: PENDING/PROCESSING/DONE/FAILED
     */
    private String status;

    private String failReason;

    private Integer retryCount;

    private Integer maxRetries;

    private String outputImageUrl;

    private String outputLocalPath;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public String getOutputImageUrl() { return outputImageUrl; }
    public void setOutputImageUrl(String outputImageUrl) { this.outputImageUrl = outputImageUrl; }

    public String getOutputLocalPath() { return outputLocalPath; }
    public void setOutputLocalPath(String outputLocalPath) { this.outputLocalPath = outputLocalPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
