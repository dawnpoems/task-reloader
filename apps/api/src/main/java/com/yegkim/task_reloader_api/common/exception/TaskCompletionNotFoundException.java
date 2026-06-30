package com.yegkim.task_reloader_api.common.exception;

public class TaskCompletionNotFoundException extends RuntimeException {
    public TaskCompletionNotFoundException(Long id) {
        super("완료 기록을 찾을 수 없습니다: id=" + id);
    }
}
