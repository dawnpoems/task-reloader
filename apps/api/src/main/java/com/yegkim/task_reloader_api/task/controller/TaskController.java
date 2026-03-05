package com.yegkim.task_reloader_api.task.controller;

import com.yegkim.task_reloader_api.common.response.ApiResponse;
import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.dto.UpdateTaskRequest;
import com.yegkim.task_reloader_api.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tasks", description = "반복 작업 관리 API")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "전체 작업 목록 조회")
    @GetMapping
    public ApiResponse<List<TaskResponse>> findAll() {
        return ApiResponse.success(taskService.findAll());
    }

    @Operation(summary = "작업 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(taskService.findById(id));
    }

    @Operation(summary = "작업 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.success(taskService.create(request));
    }

    @Operation(summary = "작업 수정")
    @PatchMapping("/{id}")
    public ApiResponse<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return ApiResponse.success(taskService.update(id, request));
    }

    @Operation(summary = "작업 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ApiResponse.success(null);
    }
}

