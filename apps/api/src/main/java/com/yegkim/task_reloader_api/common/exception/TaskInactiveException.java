package com.yegkim.task_reloader_api.common.exception;

public class TaskInactiveException extends RuntimeException {
    public TaskInactiveException(Long id) {
        super("비활성화된 작업입니다. ID: " + id);
    }
}

