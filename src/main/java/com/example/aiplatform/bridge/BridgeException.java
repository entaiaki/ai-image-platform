package com.example.aiplatform.bridge;

public class BridgeException extends RuntimeException {

    private final String requestId;

    public BridgeException(String requestId, String message) {
        super(message);
        this.requestId = requestId;
    }

    public BridgeException(String requestId, String message, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
