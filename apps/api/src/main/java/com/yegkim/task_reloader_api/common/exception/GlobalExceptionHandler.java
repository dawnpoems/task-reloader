package com.yegkim.task_reloader_api.common.exception;

import com.yegkim.task_reloader_api.auth.exception.AuthException;
import com.yegkim.task_reloader_api.common.response.ApiResponse;
import com.yegkim.task_reloader_api.common.response.ErrorResponse;
import com.yegkim.task_reloader_api.common.web.RequestIdLoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ApiResponse<Void> error(String code, String message) {
        return ApiResponse.error(ErrorResponse.of(code, message, currentRequestId()));
    }

    private String currentRequestId() {
        return MDC.get(RequestIdLoggingFilter.REQUEST_ID_MDC_KEY);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("잘못된 요청입니다.");
        log.warn("Validation failed requestId={} message={}", currentRequestId(), message);
        return error("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("'%s'은(는) 올바르지 않은 값입니다.", ex.getValue());
        log.warn("Type mismatch requestId={} message={}", currentRequestId(), message);
        return error("INVALID_PARAMETER", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request requestId={} message={}", currentRequestId(), ex.getMessage());
        return error("BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException ex) {
        log.warn("Auth exception requestId={} status={} code={} message={}", currentRequestId(), ex.getStatus(), ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleTaskNotFound(TaskNotFoundException ex) {
        log.warn("Task not found requestId={} message={}", currentRequestId(), ex.getMessage());
        return error("TASK_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(TaskInactiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleTaskInactive(TaskInactiveException ex) {
        log.warn("Task inactive requestId={} message={}", currentRequestId(), ex.getMessage());
        return error("TASK_INACTIVE", ex.getMessage());
    }

    @ExceptionHandler(TaskRecentlyCompletedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleTaskRecentlyCompleted(TaskRecentlyCompletedException ex) {
        log.warn("Task recently completed requestId={} message={}", currentRequestId(), ex.getMessage());
        return error("TASK_RECENTLY_COMPLETED", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unhandled exception requestId={}", currentRequestId(), ex);
        return error("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");
    }
}
