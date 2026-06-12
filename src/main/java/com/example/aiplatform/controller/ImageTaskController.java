package com.example.aiplatform.controller;

import com.example.aiplatform.common.Result;
import com.example.aiplatform.entity.ImageTask;
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
     * 提交任务（仅落库，后续模块会入 Redis 队列）
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(Authentication authentication,
                                              @RequestParam @NotBlank String prompt,
                                              @RequestParam(required = false) String negativePrompt) {
        // JwtFilter 中把 principal 放的是 username；企业级应放 userId，这里先简化。
        // 当前阶段临时要求：通过 username 还原 userId 需要查库（会增加依赖）。
        // 因此：先要求调用方在后续模块使用 userId 注入 principal。当前阶段先拒绝。
        if (authentication == null || authentication.getName() == null) {
            return Result.fail(401, "Unauthorized");
        }
        return Result.fail(500, "Principal currently holds username only. Next module will upgrade principal to userId.");
    }

    @GetMapping("/{id}")
    public Result<ImageTask> get(@PathVariable Long id) {
        return Result.ok(imageTaskService.getById(id));
    }

    @GetMapping("/my")
    public Result<List<ImageTask>> myList(@RequestParam(defaultValue = "50") int limit) {
        return Result.fail(500, "Not implemented yet: need userId in principal. Next module will fix.");
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
}
