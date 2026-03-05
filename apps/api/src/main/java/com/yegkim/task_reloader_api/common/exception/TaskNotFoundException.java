package com.yegkim.task_reloader_api.common.exception;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(Long id) {
        super("작업을 찾을 수 없습니다. ID: " + id);
    }

    public TaskNotFoundException(String message) {
        super(message);
    }
}

