package com.yegkim.task_reloader_api.common.exception;

public class TaskRecentlyCompletedException extends RuntimeException {
    public TaskRecentlyCompletedException(Long id) {
        super("최근에 이미 완료된 작업입니다. ID: " + id);
    }
}

