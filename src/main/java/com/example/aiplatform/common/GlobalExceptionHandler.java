package com.example.aiplatform.common;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(x -> x.getDefaultMessage())
                .orElse("Validation error");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleBadRequest(IllegalArgumentException e) {
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleForbidden(IllegalStateException e) {
        return Result.fail(403, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        return Result.fail(500, "Internal server error: " + e.getMessage());
    }
}
