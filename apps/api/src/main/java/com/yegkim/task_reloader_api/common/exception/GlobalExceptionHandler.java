package com.yegkim.task_reloader_api.common.exception;

import com.yegkim.task_reloader_api.common.response.ApiResponse;
import com.yegkim.task_reloader_api.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("잘못된 요청입니다.");
        log.warn("Validation failed: {}", message);
        return ApiResponse.error(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("'%s'은(는) 올바르지 않은 값입니다.", ex.getValue());
        log.warn("Type mismatch: {}", message);
        return ApiResponse.error(ErrorResponse.of("INVALID_PARAMETER", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ApiResponse.error(ErrorResponse.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleTaskNotFound(TaskNotFoundException ex) {
        log.warn("Task not found: {}", ex.getMessage());
        return ApiResponse.error(ErrorResponse.of("TASK_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.error(ErrorResponse.of("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}

