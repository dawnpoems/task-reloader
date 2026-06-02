# recent-completions 500 원인 분석 및 수정 기록

이 문서는 `GET /api/insights/recent-completions`에서 발생한 간헐적 500 오류를 어떻게 좁혀 갔고, 어떤 방식으로 수정했는지 정리한다.
전체 부하테스트 흐름은 [load-testing-summary.md](../load-testing-summary.md)에서 다루고, 이 문서는 하나의 장애 분석에 집중한다.

## 1. 문제 요약

고정 access token 기반 mixed 부하테스트에서 인증 재로그인/rate-limit 변수는 제거되었지만, `GET /api/insights/recent-completions`에 간헐적 500이 남았다.
전체 실패율은 낮았지만 5xx가 특정 endpoint에 집중되었고, requestId 로그에서 실제 서버 예외가 확인되었다.
따라서 단순 테스트 노이즈가 아니라 API 조회 로직의 안정성 문제로 판단했다.

## 2. 발생 조건

- 테스트 유형: read/write mixed 부하
- 인증 조건: fixed access token, `RELOGIN_ON_401=false`
- read flow: 인사이트 API와 작업 조회 API를 반복 호출
- write flow: `POST /api/tasks` -> `PATCH /api/tasks/{id}` -> `POST /api/tasks/{id}/complete` -> `DELETE /api/tasks/{id}`
- 재현 특징: read-only 시나리오에서는 관찰되지 않았고, task 완료 이력 조회와 task 삭제가 섞인 mixed 조건에서 관찰됨

## 3. 관찰된 증상

| 관찰 지점 | 내용 |
| --- | --- |
| k6 | HTTP fail `0.0243%`가 남음 |
| Grafana | 5xx Endpoint Top5에 `GET /api/insights/recent-completions` 표시 |
| Access log | 해당 endpoint의 500 access log 확인 |
| 인증 상태 | fixed-token 조건으로 401/429 변수는 제거됨 |
| requestId | 500 요청의 requestId로 API stack trace 추적 가능 |

## 4. 원인 추적 과정

1. 초기 mixed/soak에서는 `401`, `429`, `500`이 함께 발생해 인증 재로그인/rate-limit 변수와 API 본체 문제를 분리하기 어려웠다.
2. fixed-token mixed 테스트로 인증 재로그인/rate-limit 변수를 제거했다.
3. 대량 실패는 사라졌지만 `recent-completions` 500이 소량 남았다.
4. Grafana `5xx Endpoint Top5`에서 500이 해당 endpoint에 집중된 것을 확인했다.
5. access log의 requestId로 Docker/API 로그를 추적했다.
6. stack trace에서 `jakarta.persistence.EntityNotFoundException`을 확인했다.
7. 코드 경로를 따라가며 DTO 변환 중 `completion.getTask().getName()` lazy loading이 발생하던 지점을 확인했다.
8. mixed write flow의 task 삭제와 recent completion 조회가 겹칠 수 있다는 점을 시나리오와 연결했다.

## 5. 원인

`TaskCompletion`은 완료 이력을 저장하고, 기존 조회 로직은 완료 이력 엔티티를 읽은 뒤 연결된 `Task`를 lazy loading으로 참조해 응답 DTO를 만들었다.
하지만 mixed write flow에서는 task 생성, 완료, 삭제가 반복된다.
조회 트랜잭션이 `TaskCompletion`을 먼저 읽은 뒤 DTO 변환 시점에 `Task` proxy를 초기화하려고 할 때, 연결된 task row가 이미 삭제되어 있으면 `EntityNotFoundException`이 발생한다.

```text
TaskCompletion 목록 조회
-> DTO 변환
-> completion.getTask().getName()
-> Task lazy loading
-> 연결된 Task row가 이미 삭제됨
-> EntityNotFoundException
-> 500 응답
```

`task_completions.task_id`는 `tasks(id) ON DELETE CASCADE`를 참조한다.
따라서 write flow에서 `Task`가 삭제되면 연결된 완료 이력도 함께 정리될 수 있다.
문제는 read 트랜잭션이 이미 `TaskCompletion` 목록을 읽은 뒤, DTO 변환 시점에 뒤늦게 연결된 `Task`를 lazy loading하려 할 때 발생했다.
이 사이에 write flow가 `Task`를 삭제하면, 이미 읽어온 completion 객체에서 연결 `Task`를 초기화하는 과정에서 `EntityNotFoundException`이 발생할 수 있었다.

## 6. 수정 방향

수정 방향은 엔티티 조회 후 lazy 연관관계를 따라가지 않는 것이다.
응답에 필요한 필드를 repository에서 명시적으로 조회하고, service에서는 삭제될 수 있는 `Task` proxy에 의존하지 않도록 했다.
이 수정은 read/write 동시성 자체를 제거하는 것이 아니라, 응답 생성 과정에서 이미 삭제되었을 수 있는 연관 엔티티를 뒤늦게 lazy loading하는 경로를 제거하는 데 초점을 둔다.

| 대안 | 판단 |
| --- | --- |
| fetch join | lazy loading 횟수는 줄일 수 있지만, 응답 전용 조회에 필요한 필드보다 넓은 엔티티를 다룸 |
| soft delete | 참조 안정성은 좋아지지만 삭제 정책과 도메인 의미가 바뀜 |
| completion에 taskName snapshot 저장 | 장기적으로 검토할 수 있지만 schema/도메인 변경 범위가 큼 |
| DTO projection | 현재 500 원인에 직접 대응하고 변경 범위가 작음 |

최종적으로 DTO projection을 선택했다.

## 7. 수정 내용

### 수정 전

- `TaskCompletion` 엔티티 목록 조회
- service에서 `RecentTaskCompletionResponse`로 변환
- 변환 중 `completion.getTask()` 접근

### 수정 후

- repository에서 `TaskCompletion`과 `Task`를 join
- 필요한 필드만 `RecentTaskCompletionResponse` DTO projection으로 직접 조회
- service에서는 projection 결과를 그대로 반환

적용한 API:

- `GET /api/insights/recent-completions`
- `GET /api/insights/today-completions`

`today-completions`도 동일한 완료 이력 DTO 변환 경로를 사용하고 있었기 때문에, 같은 lazy loading 리스크를 제거하기 위해 함께 수정했다.

관련 구현:

- [TaskCompletionRepository.java](../../apps/api/src/main/java/com/yegkim/task_reloader_api/task/repository/TaskCompletionRepository.java)
- [TaskService.java](../../apps/api/src/main/java/com/yegkim/task_reloader_api/task/service/TaskService.java)
- [RecentTaskCompletionResponse.java](../../apps/api/src/main/java/com/yegkim/task_reloader_api/task/dto/RecentTaskCompletionResponse.java)

## 8. 검증 결과

| 검증 | 결과 |
| --- | --- |
| Fixed Token Mixed 재검증 | 실패율 `0%`, checks `100%` |
| `recent-completions` endpoint | 500 재발 없음 |
| `insights_recent` | `111,800` requests, p95 `2.17ms`, p99 `2.72ms` |
| Fixed Token Soak | `60 VU`, `2h`, `3,507,776` requests, 실패율 `0%` |
| 로그 추출 | 5xx/500/401/429 `0건` |

동일 mixed 부하에서 실패율이 `0%`로 떨어졌고, 이후 2시간 fixed-token soak에서도 5xx/500/401/429가 재발하지 않았다.

## 9. 재발 방지 / 후속 조치

- `today-completions`도 같은 DTO 변환 경로를 사용하고 있어 projection으로 함께 전환했다.
- `overview`는 직접 500이 재현된 endpoint는 아니었지만, 완료 이력과 task 정보를 함께 다루므로 projection 기반으로 선제 리팩터링했다.
- 부하테스트 종료 후 5xx access log, requestId trace, exception summary를 결과 폴더에 자동 저장하도록 관측성을 강화했다.
- 인사이트 조회 API에서는 응답 전용 DTO projection을 우선 사용하고, 엔티티 lazy 연관관계 접근을 지양하는 원칙을 세웠다.

## 10. 배운 점

낮은 실패율이라도 5xx는 서버 내부 예외이므로 별도로 추적해야 한다.
p95 latency가 낮아도 실패율이 높으면 안정적인 API라고 볼 수 없다.
인증 재로그인/rate-limit 변수와 API 본체 문제를 분리해야 원인을 정확히 볼 수 있다.
JPA lazy loading은 N+1 성능 문제뿐 아니라 삭제 경합 상황에서 안정성 문제로도 이어질 수 있다.
부하테스트는 최대 처리량 측정뿐 아니라 숨은 read/write 경합을 찾는 도구가 될 수 있다.

## 11. 관련 문서

- [Load Testing Summary](../load-testing-summary.md)
- [Fixed Token Mixed 결과](../../infra/load/results/local-mixed-fixed-token-20260530-121407/README.md)
- [500 원인 분석 원본](../../infra/load/results/local-mixed-fixed-token-20260530-121407/500-root-cause-analysis.md)
- [Fixed Token Mixed 재검증](../../infra/load/results/local-mixed-fixed-token-20260531-005152/README.md)
- [Fixed Token Soak 결과](../../infra/load/results/local-soak-fixed-token-20260531-022322/README.md)
