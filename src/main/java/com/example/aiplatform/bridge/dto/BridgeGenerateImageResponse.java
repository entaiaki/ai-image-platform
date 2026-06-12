package com.example.aiplatform.bridge.dto;

public class BridgeGenerateImageResponse {
    private String requestId;
    private String status;
    private String imageUrl;
    private String localPath;
    private String message;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
