package com.yegkim.task_reloader_api.task.event;

import com.yegkim.task_reloader_api.common.web.RequestIdLoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class TaskEventLogListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent event) {
        log.info(
                "event=task_created taskId={} everyNDays={} isActive={} requestId={}",
                event.taskId(),
                event.everyNDays(),
                event.isActive(),
                currentRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskUpdated(TaskUpdatedEvent event) {
        log.info(
                "event=task_updated taskId={} everyNDays={} isActive={} startDate={} requestId={}",
                event.taskId(),
                event.everyNDays(),
                event.isActive(),
                event.startDate(),
                currentRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskDeleted(TaskDeletedEvent event) {
        log.info("event=task_deleted taskId={} requestId={}", event.taskId(), currentRequestId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCompleted(TaskCompletedEvent event) {
        log.info(
                "event=task_completed taskId={} completedAt={} previousDueAt={} nextDueAt={} requestId={}",
                event.taskId(),
                event.completedAt(),
                event.previousDueAt(),
                event.nextDueAt(),
                currentRequestId()
        );
    }

    @EventListener
    public void onTaskCompleteRejected(TaskCompleteRejectedEvent event) {
        log.warn(
                "event=task_complete_rejected reason={} taskId={} requestId={}",
                event.reason(),
                event.taskId(),
                currentRequestId()
        );
    }

    private String currentRequestId() {
        return MDC.get(RequestIdLoggingFilter.REQUEST_ID_MDC_KEY);
    }
}
