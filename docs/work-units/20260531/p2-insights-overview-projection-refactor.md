# Work Unit: p2-insights-overview-projection-refactor

## 문제

`GET /api/insights/overview`는 완료 이력을 `TaskCompletion` 엔티티로 조회한 뒤 `completion.getTask()`를 통해 작업 정보를 읽고 있었다.

서비스 트랜잭션 안에서 DTO를 만들기 때문에 즉시 `LazyInitializationException`으로 이어질 가능성은 낮았지만, 완료 이력 수가 늘어나면 `TaskCompletion.task` lazy 초기화로 인한 추가 조회가 발생할 수 있었다.

## 결정

`overview` 계산에 필요한 값만 repository에서 DTO projection으로 조회하도록 변경했다.

- `taskId`
- `taskName`
- `completedAt`
- `previousDueAt`

## 작업 내용

- `TaskCompletionInsightRow` DTO 추가
- `TaskCompletionRepository.findInsightRowsByUserIdAndCompletedAtRange` JPQL projection 쿼리 추가
- `TaskService.getInsightsOverview`가 `TaskCompletion` 엔티티 대신 projection row를 사용하도록 변경
- 서비스 단위 테스트 fixture를 projection row 기준으로 변경
- repository 통합 테스트에 overview projection 쿼리 검증 추가

## 테스트

- `./gradlew :apps:api:compileJava`
- `./gradlew :apps:api:test`
