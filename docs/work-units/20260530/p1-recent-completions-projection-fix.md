# Work Unit: p1-recent-completions-projection-fix

## 문제

고정 토큰 mixed 부하테스트에서 `GET /api/insights/recent-completions`에 간헐적 `500`이 발생했다.

재현 직후 로그를 추출한 결과, `TaskService.toRecentCompletionResponse()`에서 `completion.getTask().getName()` 호출 중 `jakarta.persistence.EntityNotFoundException`이 발생했다. mixed write flow가 task를 생성/완료/삭제하는 동안, recent completion 조회가 `TaskCompletion`을 먼저 읽고 이후 `Task` lazy proxy를 초기화하면서 삭제된 task row를 다시 조회한 것이 원인이었다.

## 결정

`recent-completions`와 `today-completions` 조회를 projection 기반으로 변경한다.

- `TaskCompletion` entity를 반환받아 DTO 변환 중 `completion.getTask()`를 호출하지 않는다.
- repository JPQL에서 `TaskCompletion`과 `Task`를 join한다.
- 필요한 필드를 `RecentTaskCompletionResponse`로 직접 생성한다.

## 트레이드오프

- 장점: lazy loading 시점이 사라져 read/write 삭제 경합 중 `EntityNotFoundException`이 발생하지 않는다.
- 장점: 응답 DTO에 필요한 필드만 조회하므로 조회 의도가 명확하다.
- 비용: repository에 응답 전용 projection query가 추가된다.

## 작업 내용

- `RecentTaskCompletionResponse`에 JPQL constructor expression용 생성자 추가
- `TaskCompletionRepository`에 recent/today completion projection query 추가
- `TaskService.findRecentCompletions()`와 `findTodayCompletions()`가 projection 결과를 직접 반환하도록 변경
- 기존 `toRecentCompletionResponse()` 제거
- fixed-token mixed 결과 README에 500 원인 확정 로그와 수정 방식 정리
- 별도 `500-root-cause-analysis.md` 문서 추가

## 테스트 방법

- `./gradlew :apps:api:compileJava`
- `./gradlew :apps:api:test`

## 관련 테스트

- `TaskServiceTest`
- `TaskCompletionRepositoryTest`
