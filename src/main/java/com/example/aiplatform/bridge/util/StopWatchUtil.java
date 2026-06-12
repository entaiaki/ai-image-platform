package com.example.aiplatform.bridge.util;

public class StopWatchUtil {
    public static long now() {
        return System.currentTimeMillis();
    }

    public static long costMs(long start) {
        return System.currentTimeMillis() - start;
    }
}
