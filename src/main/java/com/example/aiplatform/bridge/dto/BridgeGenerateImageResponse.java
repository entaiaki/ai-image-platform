package com.example.aiplatform.bridge.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class BridgeGenerateImageResponse {

    @JsonAlias({"requestId", "request_id"})
    private String requestId;

    /**
     * 可能值：DONE/FAILED/...
     */
    @JsonAlias({"status"})
    private String status;

    @JsonAlias({"imageUrl", "image_url", "resultUrl", "result_url"})
    private String imageUrl;

    @JsonAlias({"localPath", "local_path", "imagePath", "image_path", "outputPath", "output_path"})
    private String localPath;

    /** Bridge v03 actual fields */
    @JsonAlias({"image_paths", "imagePaths"})
    private java.util.List<String> imagePaths;

    @JsonAlias({"message", "msg", "error", "error_message"})
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

    public java.util.List<String> getImagePaths() { return imagePaths; }
    public void setImagePaths(java.util.List<String> imagePaths) { this.imagePaths = imagePaths; }

    @Override
    public String toString() {
        return "BridgeGenerateImageResponse{" +
                "requestId='" + requestId + '\'' +
                ", status='" + status + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", localPath='" + localPath + '\'' +
                ", imagePaths=" + imagePaths +
                ", message='" + message + '\'' +
                '}';
    }
}
