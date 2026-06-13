package com.example.aiplatform.controller;

import com.example.aiplatform.common.Result;
import com.example.aiplatform.entity.ImageTask;
import com.example.aiplatform.security.CustomUserPrincipal;
import com.example.aiplatform.service.ImageTaskService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class ImageTaskController {

    private final ImageTaskService imageTaskService;

    public ImageTaskController(ImageTaskService imageTaskService) {
        this.imageTaskService = imageTaskService;
    }

    /**
     * 提交任务：落库 + 入 Redis Queue
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(Authentication authentication,
                                              @RequestParam("prompt") @NotBlank String prompt,
                                              @RequestParam(value = "negativePrompt", required = false) String negativePrompt) {
        CustomUserPrincipal p = requirePrincipal(authentication);

        ImageTask t = imageTaskService.createTaskAndEnqueue(p.getUserId(), prompt, negativePrompt);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", t.getId());
        data.put("requestId", t.getRequestId());
        data.put("status", t.getStatus());
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result<ImageTask> get(@PathVariable("id") Long id) {
        return Result.ok(imageTaskService.getById(id));
    }

    @GetMapping("/my")
    public Result<List<ImageTask>> myList(Authentication authentication,
                                         @RequestParam(value = "limit", defaultValue = "50") int limit) {
        CustomUserPrincipal p = requirePrincipal(authentication);
        return Result.ok(imageTaskService.listByUser(p.getUserId(), limit));
    }

    /**
     * 仅用于演示状态机流转接口（后续将由 Python Bridge 回调/worker 调用）
     */
    @PostMapping("/{id}/processing")
    public Result<Void> markProcessing(@PathVariable Long id) {
        imageTaskService.markProcessing(id);
        return Result.ok();
    }

    @PostMapping("/{id}/done")
    public Result<Void> markDone(@PathVariable Long id,
                                 @RequestParam(required = false) String outputImageUrl,
                                 @RequestParam(required = false) String outputLocalPath) {
        imageTaskService.markDone(id, outputImageUrl, outputLocalPath);
        return Result.ok();
    }

    @PostMapping("/{id}/failed")
    public Result<Void> markFailed(@PathVariable Long id,
                                   @RequestParam String reason) {
        imageTaskService.markFailed(id, reason);
        return Result.ok();
    }

    private CustomUserPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Unauthorized");
        }
        if (authentication.getPrincipal() instanceof CustomUserPrincipal p) {
            return p;
        }
        throw new IllegalStateException("Invalid principal type");
    }
}
