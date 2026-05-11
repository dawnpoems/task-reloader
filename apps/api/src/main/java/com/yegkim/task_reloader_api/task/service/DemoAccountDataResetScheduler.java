package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.auth.entity.RefreshToken;
import com.yegkim.task_reloader_api.auth.entity.User;
import com.yegkim.task_reloader_api.auth.repository.RefreshTokenRepository;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.repository.TaskCompletionRepository;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "demo.account.reset", name = "enabled", havingValue = "true")
public class DemoAccountDataResetScheduler {

    private static final List<SeedTaskSpec> DEFAULT_SEED_TASKS = List.of(
            new SeedTaskSpec("아침 스트레칭", 1, 0),
            new SeedTaskSpec("물 2L 마시기", 1, -1),
            new SeedTaskSpec("영어 단어 20개", 2, -1),
            new SeedTaskSpec("운동 30분", 3, 1),
            new SeedTaskSpec("주간 리뷰", 7, 2),
            new SeedTaskSpec("독서 20분", 1, 0)
    );

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskCompletionRepository taskCompletionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Value("${demo.account.reset.email:demo@dawnpoem.kr}")
    private String demoAccountEmail;

    @Value("${demo.account.reset.zone-id:Asia/Seoul}")
    private String resetZoneId;

    @Value("${demo.account.reset.seed-enabled:true}")
    private boolean seedEnabled;

    @Scheduled(
            cron = "${demo.account.reset.cron:0 0 4 * * *}",
            zone = "${demo.account.reset.zone-id:Asia/Seoul}"
    )
    @Transactional
    public void resetDemoAccountDataDaily() {
        resetDemoAccountData("scheduled");
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resetDemoAccountDataOnStartup() {
        resetDemoAccountData("startup");
    }

    private void resetDemoAccountData(String trigger) {
        String normalizedEmail = normalizeEmail(demoAccountEmail);
        User demoUser = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (demoUser == null) {
            log.warn("Demo account reset skipped. trigger={} user not found email={}", trigger, normalizedEmail);
            return;
        }

        Long userId = demoUser.getId();
        long activeTaskCount = taskRepository.countByUserIdAndIsActiveTrue(userId);
        long totalTaskCount = taskRepository.countByUserId(userId);
        long completionCount = taskCompletionRepository.countByUserId(userId);

        OffsetDateTime nowUtc = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        activeTokens.forEach(token -> token.revoke(nowUtc));

        long deletedTaskCount = taskRepository.deleteByUserId(userId);
        int seededTaskCount = seedEnabled ? seedDefaultTasks(userId) : 0;

        log.info(
                "Demo account reset completed trigger={} email={} userId={} activeTasksBefore={} totalTasksBefore={} completionsBefore={} revokedTokens={} deletedTasks={} seededTasks={}",
                trigger,
                normalizedEmail,
                userId,
                activeTaskCount,
                totalTaskCount,
                completionCount,
                activeTokens.size(),
                deletedTaskCount,
                seededTaskCount
        );
    }

    private int seedDefaultTasks(Long userId) {
        LocalDate today = LocalDate.now(resolveZoneId());
        List<Task> seedTasks = DEFAULT_SEED_TASKS.stream()
                .map(spec -> Task.builder()
                        .userId(userId)
                        .name(spec.name())
                        .everyNDays(spec.everyNDays())
                        .startDate(today.plusDays(spec.startDateOffsetDays()))
                        .isActive(true)
                        .build())
                .toList();

        taskRepository.saveAll(seedTasks);
        return seedTasks.size();
    }

    private ZoneId resolveZoneId() {
        return ZoneId.of(resetZoneId);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record SeedTaskSpec(String name, int everyNDays, long startDateOffsetDays) {
    }
}
