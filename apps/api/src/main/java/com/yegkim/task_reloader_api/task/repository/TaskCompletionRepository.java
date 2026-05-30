package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse;
import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, Long> {

    List<TaskCompletion> findByTaskIdOrderByCompletedAtDesc(Long taskId);

    List<TaskCompletion> findByTaskIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
            Long taskId,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    List<TaskCompletion> findByUserIdAndTaskIdOrderByCompletedAtDesc(Long userId, Long taskId);
    List<TaskCompletion> findByUserIdAndTaskIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
            Long userId,
            Long taskId,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    List<TaskCompletion> findTop5ByOrderByCompletedAtDesc();

    List<TaskCompletion> findTop5ByUserIdOrderByCompletedAtDesc(Long userId);

    @Query("""
            select new com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse(
                c.id,
                t.id,
                t.name,
                c.completedAt,
                c.previousDueAt,
                c.nextDueAt
            )
            from TaskCompletion c
            join c.task t
            where c.userId = :userId
            order by c.completedAt desc
            """)
    List<RecentTaskCompletionResponse> findRecentCompletionResponsesByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );

    List<TaskCompletion> findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    List<TaskCompletion> findByUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long userId,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    List<TaskCompletion> findByUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
            Long userId,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    @Query("""
            select new com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse(
                c.id,
                t.id,
                t.name,
                c.completedAt,
                c.previousDueAt,
                c.nextDueAt
            )
            from TaskCompletion c
            join c.task t
            where c.userId = :userId
              and c.completedAt >= :startInclusive
              and c.completedAt < :endExclusive
            order by c.completedAt desc
            """)
    List<RecentTaskCompletionResponse> findRecentCompletionResponsesByUserIdAndCompletedAtRange(
            @Param("userId") Long userId,
            @Param("startInclusive") OffsetDateTime startInclusive,
            @Param("endExclusive") OffsetDateTime endExclusive
    );

    long countByCompletedAtBetween(OffsetDateTime start, OffsetDateTime end);

    long countByUserIdAndCompletedAtBetween(Long userId, OffsetDateTime start, OffsetDateTime end);

    long countByUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long userId,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    long countByUserId(Long userId);

    long countByCompletedAtGreaterThanEqual(OffsetDateTime start);

    long countByUserIdAndCompletedAtGreaterThanEqual(Long userId, OffsetDateTime start);
}
