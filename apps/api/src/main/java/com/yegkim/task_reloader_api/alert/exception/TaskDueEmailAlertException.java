package com.yegkim.task_reloader_api.alert.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TaskDueEmailAlertException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TaskDueEmailAlertException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
