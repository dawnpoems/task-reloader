# 작업 단위: p1-task-due-email-alert-aggregation

## 문제
- 작업 마감 이메일 알림을 보내려면 사용자별로 오늘 마감 작업과 지난 작업을 먼저 집계해야 한다.
- 전체 작업을 애플리케이션으로 가져와 분류하면 불필요한 조회가 늘어나므로, DB에서 필요한 범위를 바로 조회할 필요가 있다.

## 결정
- `TaskRepository`에 사용자/활성상태/`next_due_at` 범위 조건을 포함한 조회 메서드를 추가한다.
- 집계 서비스는 사용자 타임존 기준으로 오늘 시작/내일 시작 경계를 계산하고, `next_due_at ASC` 정렬 결과를 메일용 요약 DTO로 변환한다.

## 트레이드오프
- 장점: 기존 인덱스 `(user_id, is_active, next_due_at)`를 활용하기 쉬워지고, 발송 대상 작업만 조회할 수 있다.
- 단점: today/overdue 조회를 위해 repository 메서드가 늘어난다.

## 구현 요약
- `TaskDueEmailAlertAggregationService` 추가
- `TaskDueEmailAlertSummary`, `TaskDueEmailAlertTaskItem` DTO 추가
- `TaskRepository`에 overdue/due today 범위 조회 메서드 추가
- 사용자 타임존 기준 `localDate`, `dueDate`, `isEmpty()` 판단을 집계 결과에 포함
- 집계 서비스 단위 테스트와 repository 범위 조회 테스트 추가

## 테스트 방법
- `TaskDueEmailAlertAggregationServiceTest`에서 사용자 타임존 경계, 로컬 날짜, due date 변환, 0건 여부, 잘못된 타임존 예외를 검증한다.
- `TaskRepositoryTest`에서 overdue/due today 범위 조회가 `next_due_at ASC` 정렬과 경계 포함/제외 규칙을 지키는지 검증한다.

## 관련 테스트
- `apps/api/src/test/java/com/yegkim/task_reloader_api/alert/service/TaskDueEmailAlertAggregationServiceTest.java`
- `apps/api/src/test/java/com/yegkim/task_reloader_api/task/repository/TaskRepositoryTest.java`

## 한 줄 요약
- 작업 마감 이메일 발송 전에 사용할 due today/overdue 작업 집계 서비스와 검증 테스트를 추가했다.
