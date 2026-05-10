package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.auth.entity.RefreshToken;
import com.yegkim.task_reloader_api.auth.entity.User;
import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;
import com.yegkim.task_reloader_api.auth.repository.RefreshTokenRepository;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.repository.TaskCompletionRepository;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DemoAccountDataResetScheduler 단위테스트")
class DemoAccountDataResetSchedulerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-10T04:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskCompletionRepository taskCompletionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private DemoAccountDataResetScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "demoAccountEmail", "demo@dawnpoem.kr");
        ReflectionTestUtils.setField(scheduler, "resetZoneId", "Asia/Seoul");
        ReflectionTestUtils.setField(scheduler, "seedEnabled", true);
        lenient().when(clock.instant()).thenReturn(FIXED_NOW);
        lenient().when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("데모 계정이 없으면 리셋 로직을 수행하지 않는다")
    void resetDemoAccountDataDaily_userNotFound_skipsReset() {
        when(userRepository.findByEmail("demo@dawnpoem.kr")).thenReturn(Optional.empty());

        scheduler.resetDemoAccountDataDaily();

        verify(taskRepository, never()).deleteByUserId(org.mockito.ArgumentMatchers.anyLong());
        verify(refreshTokenRepository, never()).findAllByUserIdAndRevokedAtIsNull(org.mockito.ArgumentMatchers.anyLong());
        verify(taskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("seed 활성화 상태면 데이터 초기화 후 샘플 작업을 재생성한다")
    void resetDemoAccountDataDaily_seedEnabled_resetsAndSeedsTasks() {
        User demoUser = user(101L, "demo@dawnpoem.kr");
        RefreshToken tokenA = refreshToken(demoUser, "token-hash-a");
        RefreshToken tokenB = refreshToken(demoUser, "token-hash-b");

        when(userRepository.findByEmail("demo@dawnpoem.kr")).thenReturn(Optional.of(demoUser));
        when(taskRepository.countByUserIdAndIsActiveTrue(101L)).thenReturn(4L);
        when(taskRepository.countByUserId(101L)).thenReturn(7L);
        when(taskCompletionRepository.countByUserId(101L)).thenReturn(19L);
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(101L)).thenReturn(List.of(tokenA, tokenB));
        when(taskRepository.deleteByUserId(101L)).thenReturn(7L);

        scheduler.resetDemoAccountDataDaily();

        OffsetDateTime expectedRevokedAt = OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC);
        assertThat(tokenA.getRevokedAt()).isEqualTo(expectedRevokedAt);
        assertThat(tokenB.getRevokedAt()).isEqualTo(expectedRevokedAt);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Task>> seedTasksCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(seedTasksCaptor.capture());

        List<Task> seededTasks = seedTasksCaptor.getValue();
        assertThat(seededTasks).hasSize(6);
        assertThat(seededTasks).allSatisfy(task -> {
            assertThat(task.getUserId()).isEqualTo(101L);
            assertThat(task.getIsActive()).isTrue();
        });
        assertThat(seededTasks).extracting(Task::getName).containsExactly(
                "아침 스트레칭",
                "물 2L 마시기",
                "영어 단어 20개",
                "운동 30분",
                "주간 리뷰",
                "독서 20분"
        );

        LocalDate todayKst = LocalDate.ofInstant(FIXED_NOW, java.time.ZoneId.of("Asia/Seoul"));
        assertThat(seededTasks).extracting(Task::getStartDate).containsExactly(
                todayKst,
                todayKst.minusDays(1),
                todayKst.minusDays(1),
                todayKst.plusDays(1),
                todayKst.plusDays(2),
                todayKst
        );
    }

    @Test
    @DisplayName("seed 비활성화 상태면 데이터 초기화까지만 수행한다")
    void resetDemoAccountDataDaily_seedDisabled_onlyReset() {
        ReflectionTestUtils.setField(scheduler, "seedEnabled", false);
        ReflectionTestUtils.setField(scheduler, "demoAccountEmail", " Demo@Dawnpoem.kr ");

        User demoUser = user(202L, "demo@dawnpoem.kr");
        RefreshToken token = refreshToken(demoUser, "token-hash-c");

        when(userRepository.findByEmail("demo@dawnpoem.kr")).thenReturn(Optional.of(demoUser));
        when(taskRepository.countByUserIdAndIsActiveTrue(202L)).thenReturn(2L);
        when(taskRepository.countByUserId(202L)).thenReturn(3L);
        when(taskCompletionRepository.countByUserId(202L)).thenReturn(11L);
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(202L)).thenReturn(List.of(token));
        when(taskRepository.deleteByUserId(202L)).thenReturn(3L);

        scheduler.resetDemoAccountDataDaily();

        verify(taskRepository).deleteByUserId(202L);
        verify(taskRepository, never()).saveAll(anyList());
        assertThat(token.getRevokedAt()).isEqualTo(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC));
    }

    private User user(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash("hash-" + id)
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .build();
    }

    private RefreshToken refreshToken(User user, String tokenHash) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();
    }
}
