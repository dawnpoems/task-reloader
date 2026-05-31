# `recent-completions` 500 원인 분석 및 수정 기록

본 문서는 `GET /api/insights/recent-completions`에서 관찰된 간헐적 `500`의 원인을 어떻게 찾았고, 어떤 방식으로 수정했는지 정리한 기록입니다.

## 1) 문제 발견

고정 access token 기반 mixed 부하테스트에서 인증 재시도와 rate-limit 영향은 제거되었지만, Grafana와 k6 결과에서 `GET /api/insights/recent-completions`의 간헐적 `500`이 남았습니다.

관찰된 패턴은 다음과 같습니다.

- 전체 실패율은 낮음: `0.0243%`
- k6 check 실패는 `insights_recent`에 집중
- Grafana `5xx Endpoint Top5`에서도 `500 GET /api/insights/recent-completions`만 표시
- `401/429`는 고정 토큰 조건에서 사실상 제거됨

따라서 문제 범위는 인증이나 전체 API 처리량이 아니라, `recent-completions` 조회 로직으로 좁혀졌습니다.

## 2) 원인 추적 방법

원인 추적은 requestId를 기준으로 진행했습니다.

1. Grafana에서 5xx 발생 endpoint를 확인했습니다.
2. Docker API 로그에서 `status=500` access log를 추출했습니다.
3. access log의 `requestId`를 수집했습니다.
4. 같은 `requestId`의 `Unhandled exception` stack trace를 찾았습니다.
5. stack trace의 예외 타입과 코드 라인으로 원인을 확정했습니다.

처음에는 이전 실행 시간대의 Docker 로그가 남아 있지 않아 원인을 확정하지 못했습니다. 이후 500이 재현된 직후 즉시 로그를 추출해 stack trace를 확보했습니다.

## 3) 확보한 로그 요약

재현 직후 추출한 로그 요약입니다.

```text
# Live 500 Cause Extract
since: 30m
out_dir: infra/load/results/live-500-extract-20260530-134924
total_500_access_lines: 15
recent_completions_500_lines: 15
recent_completions_request_ids: 15
```

모든 500 access log는 동일 endpoint에 집중되었습니다.

```text
access method=GET uri=/api/insights/recent-completions status=500 durationMs=2 requestId=709d8cd7-11dc-4695-ad41-9bca7ef5f295
access method=GET uri=/api/insights/recent-completions status=500 durationMs=2 requestId=124094e7-cd26-4954-9ad8-86aaf6a35e43
access method=GET uri=/api/insights/recent-completions status=500 durationMs=4 requestId=6f7626e4-3e11-491e-854d-16490fa6ff4a
```

대표 stack trace는 다음과 같습니다.

```text
jakarta.persistence.EntityNotFoundException:
No row with the given identifier exists for entity
[com.yegkim.task_reloader_api.task.entity.Task with id '125750']

at com.yegkim.task_reloader_api.task.entity.Task$HibernateProxy.getName(Unknown Source)
at com.yegkim.task_reloader_api.task.service.TaskService.toRecentCompletionResponse(TaskService.java:440)
at com.yegkim.task_reloader_api.task.service.TaskService.findRecentCompletions(TaskService.java:145)
at com.yegkim.task_reloader_api.task.controller.TaskInsightsController.getRecentCompletions(TaskInsightsController.java:44)
```

## 4) 원인 확정

원인은 `TaskCompletion -> Task` lazy loading 중 연결된 `Task` row가 이미 삭제되어 발생한 `EntityNotFoundException`입니다.

기존 조회 흐름은 다음과 같았습니다.

```text
GET /api/insights/recent-completions
-> TaskService.findRecentCompletions()
-> taskCompletionRepository.findTop5ByUserIdOrderByCompletedAtDesc(userId)
-> TaskCompletion 엔티티 목록 반환
-> TaskService.toRecentCompletionResponse()
-> completion.getTask().getName()
-> Task lazy loading
-> 연결된 Task row 없음
-> EntityNotFoundException
-> 500 응답
```

부하테스트의 mixed write flow는 아래 작업을 반복합니다.

```text
POST /api/tasks
PATCH /api/tasks/{id}
POST /api/tasks/{id}/complete
DELETE /api/tasks/{id}
```

DB 스키마는 완료 이력의 `task_id`에 cascade delete가 걸려 있습니다.

```sql
task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE
```

따라서 조회와 삭제가 고부하에서 겹치면 다음 경합이 발생할 수 있습니다.

```text
1. 조회 트랜잭션이 task_completions 목록을 읽음
2. 삭제 트랜잭션이 task를 삭제하고 completion도 cascade delete
3. 조회 트랜잭션이 DTO 변환 중 completion.getTask().getName() 호출
4. Hibernate가 Task lazy proxy를 초기화하려고 DB 조회
5. Task row가 이미 없어 EntityNotFoundException 발생
```

이 문제는 평균 latency나 RPS 문제가 아니라, read/write 경합 중 entity lazy loading 시점이 뒤로 밀리면서 발생한 일관성 문제입니다.

## 5) 수정 결정

수정 방식은 projection 조회로 결정했습니다.

### 선택한 방식

`TaskCompletion` 엔티티를 조회한 뒤 `completion.getTask()`를 호출하지 않고, repository JPQL에서 `TaskCompletion`과 `Task`를 join해 응답 DTO를 직접 생성합니다.

### 선택 이유

- lazy loading 자체를 제거할 수 있습니다.
- 조회 시점에 필요한 task 필드가 함께 확정됩니다.
- 응답 전용 API라는 의도가 repository query에 명확히 드러납니다.
- `fetch join`보다 DTO 응답에 필요한 필드만 가져오므로 API 조회 목적에 더 잘 맞습니다.

### 적용 범위

- `findRecentCompletions()`
- `findTodayCompletions()`

`today-completions`도 같은 DTO 변환 경로를 사용하고 있었기 때문에, 동일한 lazy loading 위험을 함께 제거했습니다.

## 6) 코드 수정 요약

`RecentTaskCompletionResponse`에 JPQL constructor expression을 위한 생성자를 추가했습니다.

```java
@Getter
@Builder
@AllArgsConstructor
public class RecentTaskCompletionResponse {
    private Long id;
    private Long taskId;
    private String taskName;
    private OffsetDateTime completedAt;
    private OffsetDateTime previousDueAt;
    private OffsetDateTime nextDueAt;
}
```

repository에는 응답 DTO projection 쿼리를 추가했습니다.

```java
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
```

service에서는 entity 변환 함수를 제거하고, repository의 projection 결과를 그대로 반환하도록 변경했습니다.

```java
public List<RecentTaskCompletionResponse> findRecentCompletions() {
    Long userId = authenticatedUserProvider.currentUserId();
    return taskCompletionRepository.findRecentCompletionResponsesByUserId(
            userId,
            PageRequest.of(0, RECENT_COMPLETION_LIMIT)
    );
}
```

## 7) 검증

로컬에서 아래 검증을 수행했습니다.

```text
./gradlew :apps:api:compileJava
./gradlew :apps:api:test
```

결과:

```text
BUILD SUCCESSFUL
```

테스트 보강 내용:

- `TaskServiceTest`: service가 projection repository method를 호출하도록 변경
- `TaskCompletionRepositoryTest`: PostgreSQL/Testcontainers 기반으로 projection query가 task 정보를 join해 반환하는지 검증
- 오늘 완료 이력 projection도 같은 방식으로 검증

## 8) 재확인 기준

수정 배포 후 동일 fixed-token mixed 부하를 다시 실행해 다음을 확인합니다.

- `GET /api/insights/recent-completions` 500이 0에 수렴하는지
- k6 `insights_recent: status 200` check 실패가 사라지는지
- Grafana `5xx Endpoint Top5`에 `recent-completions`가 다시 나타나지 않는지
- latency p95/p99가 기존 수준과 크게 달라지지 않는지
