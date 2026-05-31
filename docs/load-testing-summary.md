# 부하테스트 종합 정리

이 문서는 Task Reloader에서 수행한 k6/Grafana 기반 부하테스트의 전체 흐름을 정리한 기록이다.

핵심은 단순히 최대 RPS를 측정하는 것이 아니었다. 테스트 결과를 보고 문제를 분리하고, 실제 코드를 수정하고, 같은 조건 또는 더 긴 조건에서 다시 검증하는 과정을 반복했다.

## 한눈에 보는 결론

부하테스트를 통해 얻은 결론은 다음과 같다.

- Read API는 `80 VU`, 평균 `553.22 RPS`, p95 `8.11ms`, 실패율 `0%`로 안정적이었다.
- read/write 혼합 부하에서는 평균 `415.59 RPS`, peak 약 `600 RPS`까지 처리했지만, `401/429`가 대량 발생해 운영 안정성 기준에는 미달했다.
- 고정 access token으로 인증 재로그인/rate-limit 변수를 제거하자 mixed 실패율이 `45.42%`에서 `0.0243%`로 떨어졌다.
- 인증 변수를 제거한 뒤에도 남은 `GET /api/insights/recent-completions` 500은 실제 서버 예외였고, requestId 로그로 `EntityNotFoundException`을 확인했다.
- `recent-completions`, `today-completions`를 DTO projection 조회로 수정한 뒤 동일 mixed 부하에서 실패율 `0%`, checks `100%`를 확인했다.
- 이후 `/api/insights/overview`도 lazy/N+1 리스크를 줄이기 위해 projection 기반으로 선제 리팩터링했다.
- 최종 fixed-token soak에서는 `60 VU`, `2h`, 총 `3,507,776` 요청, 평균 `487.14 RPS`, 실패율 `0%`, checks `100%`, 5xx/500/401/429 `0건`을 확인했다.

현재 결론은 다음과 같다.

> 인증 재로그인/rate-limit 변수를 제거한 조건에서, API 본체와 DB 접근 패턴은 read/write 혼합 부하와 2시간 지속 부하를 안정적으로 처리했다. 특히 부하테스트로 발견한 lazy loading 기반 500은 코드 수정 후 재검증까지 완료했다.

## 전체 진행 흐름

이번 부하테스트는 한 번에 모든 것을 검증하는 방식이 아니라, 질문을 쪼개고 결과에 따라 다음 행동을 정하는 방식으로 진행했다.

| 순서 | 테스트/작업 | 확인하려던 것 | 결과 | 다음 행동 |
| --- | --- | --- | --- | --- |
| 1 | Read Matrix | read API 용량 기준선 | `80 VU`, p95 `8.11ms`, 실패율 `0%` | read는 안정적이므로 mixed 부하로 확장 |
| 2 | Mixed Peak | read/write 혼합 시 병목 | `401/429` 대량 발생, `recent-completions` 500 확인 | 인증 문제와 API 본체 문제 분리 필요 |
| 3 | 초기 Soak | 장시간 부하에서 실패 누적 | `401/429` 장시간 누적, `recent-completions` 500 반복 | fixed token 조건으로 재검증 |
| 4 | Fixed Token Mixed | 인증 변수를 제거한 API 본체 안정성 | 실패율 `0.0243%`, 남은 실패는 `recent-completions` 500 | requestId 로그로 500 원인 추적 |
| 5 | 코드 수정 1 | `recent-completions` 500 제거 | lazy loading 경합을 DTO projection으로 제거 | 동일 mixed 부하 재실행 |
| 6 | Mixed 재검증 | projection 수정 효과 | 실패율 `0%`, checks `100%` | 유사한 인사이트 조회 API 점검 |
| 7 | 코드 수정 2 | `overview` lazy/N+1 리스크 제거 | `TaskCompletionInsightRow` projection 적용 | 장시간 soak 재검증 |
| 8 | 관측성 강화 | 다음 500 발생 시 즉시 원인 추적 | 로그/5xx/requestId trace 자동 추출 추가 | 이후 테스트 결과 폴더에 원인자료 보존 |
| 9 | Fixed Token Soak | 수정 후 장시간 안정성 | `60 VU`, `2h`, `3,507,776` 요청, 실패율 `0%` | API 본체 안정성 근거 확보 |

이 흐름에서 중요한 점은 `실패 발견 -> 원인 분리 -> 코드 수정 -> 같은 조건 재검증 -> 더 긴 조건 재검증`의 루프를 만들었다는 것이다.

## 테스트별 상세 결과와 판단

### 1. Read Matrix

- 결과 문서: [local-read-matrix-20260512-102647](../infra/load/results/local-read-matrix-20260512-102647/README.md)
- 목적: 인증된 사용자의 read API가 동시 사용자 증가에 따라 어느 정도까지 안정적인지 확인
- 방식: read-only API를 `5 -> 20 -> 40 -> 60 -> 80 VU`로 단계 증가

| Case | VU | Requests | Avg RPS | p95 | p99 | Fail | Checks |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Smoke | 5 | 2,101 | 34.37 | 13.52ms | 19.21ms | 0% | 100% |
| Baseline | 20 | 83,245 | 138.54 | 7.58ms | 10.18ms | 0% | 100% |
| Step | 40 | 83,413 | 276.92 | 7.82ms | 10.92ms | 0% | 100% |
| Step | 60 | 124,965 | 414.88 | 7.91ms | 12.20ms | 0% | 100% |
| Step | 80 | 166,643 | 553.22 | 8.11ms | 12.17ms | 0% | 100% |

이 테스트로 얻은 것:

- read API는 `80 VU`까지 처리량이 거의 선형적으로 증가했다.
- 최고 부하에서도 실패율은 `0%`였고 p95는 `8.11ms`였다.
- 따라서 read API만 놓고 보면 홈서버 로컬 환경에서 80명 수준 동시접속 read 부하는 안정적으로 처리 가능하다고 판단했다.
- 다만 read-only 테스트는 쓰기 경합, 인증 만료, rate-limit, 장시간 누적 문제를 검증하지 않는다.

이후 행동:

- read 기준선은 충분히 확보되었으므로 read/write 혼합 부하로 확장했다.

### 2. Mixed Peak

- 결과 문서: [local-mixed-peak-20260517-043551](../infra/load/results/local-mixed-peak-20260517-043551/README.md)
- 목적: 실제 사용에 가까운 read/write 혼합 상황에서 병목과 실패 유형 확인
- 방식: `70%` read, `30%` write, `0 -> 20 -> 50 VU`, 50 VU 20분 유지
- 인증 조건: `RELOGIN_ON_401=true`

| Metric | Value |
| --- | ---: |
| Requests | 872,832 |
| Avg RPS | 415.59 |
| p95 | 4.10ms |
| p99 | 6.02ms |
| Max | 1,219.76ms |
| HTTP fail | 45.42% |
| Checks | 51.37% |

이 테스트로 얻은 것:

- 처리량은 peak 약 `600 RPS`까지 상승했고 latency도 낮게 보였다.
- 하지만 실패율 `45.42%`, checks `51.37%`로 안정적이라고 볼 수 없었다.
- Grafana 상태코드 분포에서 `401`, `429`가 크게 증가했다.
- 5xx는 전체 실패의 중심은 아니었지만, `GET /api/insights/recent-completions`에 집중된 500이 보였다.

판단:

- latency가 낮아도 성공률이 낮으면 운영 안정성은 확보되지 않은 것이다.
- 특히 실패 응답이 빠르게 반환되면 p95가 좋아 보일 수 있으므로, mixed 테스트에서는 latency만 보면 안 된다.
- 이 시점에서는 `인증 재로그인/rate-limit 문제`, `API 본체 처리 문제`, `recent-completions 500 문제`가 섞여 있었다.

이후 행동:

- 같은 부하를 장시간으로 늘려 실패가 누적되는지 확인했다.
- 이후 인증 변수를 제거한 fixed token 테스트를 설계했다.

### 3. 초기 Soak

- 결과 문서: [local-soak-20260517-055109](../infra/load/results/local-soak-20260517-055109/README.md)
- 목적: 장시간 steady load에서 실패가 누적되는지 확인
- 방식: `60 VU`, `2h`, `85%` read, `15%` write
- 인증 조건: `RELOGIN_ON_401=true`

| Metric | Value |
| --- | ---: |
| Requests | 3,108,003 |
| Avg RPS | 431.61 |
| p95 | 3.57ms |
| p99 | 5.42ms |
| Max | 1,288.36ms |
| HTTP fail | 62.33% |
| Checks | 35.30% |

이 테스트로 얻은 것:

- 처리량과 지연은 장시간 동안 큰 드리프트 없이 유지되었다.
- 그러나 `401/429`가 장시간 유지되며 성공률을 크게 낮췄다.
- mixed peak에서 보인 인증/보호정책 병목이 장시간 테스트에서도 반복되었다.
- `recent-completions` 500도 소량 반복되어 별도 수정 대상으로 유지했다.

판단:

- API가 느려서 실패한 것이 아니라, 인증 상태 유지와 재인증 흐름이 부하 상황에서 실패를 확산시키고 있었다.
- API 본체를 판단하려면 인증 재로그인과 rate-limit 영향을 분리해야 했다.

이후 행동:

- access token을 사전에 발급해 고정하고, `RELOGIN_ON_401=false`로 설정한 fixed token mixed 테스트를 진행했다.

### 4. Fixed Token Mixed

- 결과 문서: [local-mixed-fixed-token-20260530-121407](../infra/load/results/local-mixed-fixed-token-20260530-121407/README.md)
- 원인 분석: [500-root-cause-analysis.md](../infra/load/results/local-mixed-fixed-token-20260530-121407/500-root-cause-analysis.md)
- 목적: 인증 재로그인/rate-limit 영향을 제거한 상태에서 API 본체 처리 안정성 확인
- 방식: mixed peak와 동일한 `70:30` read/write
- 인증 조건: 사전 발급 access token 고정, `RELOGIN_ON_401=false`

| Metric | Value |
| --- | ---: |
| Requests | 970,465 |
| Avg RPS | 462.09 |
| p95 | 5.49ms |
| p99 | 6.28ms |
| Max | 773.85ms |
| HTTP fail | 0.0243% |
| Checks | 99.9757% |

이 테스트로 얻은 것:

- 고정 token 조건에서는 `401/429`가 사실상 제거되었다.
- mixed 실패율이 `45.42%`에서 `0.0243%`로 급감했다.
- 대량 실패의 중심은 API 처리량 부족이 아니라 인증 재로그인 및 rate-limit 경합이었다.
- 남은 실패는 `GET /api/insights/recent-completions` 500으로 좁혀졌다.

이후 코드 수정으로 이어진 지점:

- Grafana `5xx Endpoint Top5`에서 `GET /api/insights/recent-completions`가 반복적으로 보였다.
- requestId 기반으로 Docker 로그를 추적해 `EntityNotFoundException`을 확인했다.
- 원인은 `TaskCompletion -> Task` lazy loading 중 연결된 `Task` row가 이미 삭제되어 발생한 예외였다.

문제가 된 구조:

```text
TaskCompletion 목록 조회
-> DTO 변환 중 completion.getTask().getName() 호출
-> Task lazy loading
-> mixed write flow의 delete와 타이밍 경합
-> 연결된 Task row 없음
-> EntityNotFoundException
-> 500 응답
```

수정한 방식:

- `TaskCompletion` 엔티티를 가져온 뒤 연관 `Task`를 lazy loading하지 않도록 변경했다.
- repository JPQL에서 `TaskCompletion`과 `Task`를 join하고, 필요한 필드만 `RecentTaskCompletionResponse` DTO projection으로 바로 조회했다.
- 같은 DTO 변환 경로를 쓰는 `today-completions`도 함께 projection으로 전환했다.

이후 행동:

- 동일 fixed-token mixed 시나리오를 다시 실행해 수정 효과를 검증했다.

### 5. Projection 수정 후 Mixed 재검증

- 결과 문서: [local-mixed-fixed-token-20260531-005152](../infra/load/results/local-mixed-fixed-token-20260531-005152/README.md)
- 목적: `recent-completions`, `today-completions` projection 수정 후 500이 재발하지 않는지 확인

| Metric | Value |
| --- | ---: |
| Requests | 972,932 |
| Avg RPS | 463.20 |
| p95 | 4.92ms |
| p99 | 6.16ms |
| Max | 65.61ms |
| HTTP fail | 0% |
| Checks | 100% |

핵심 endpoint 결과:

| Endpoint | Requests | p95 | p99 |
| --- | ---: | ---: | ---: |
| `insights_recent` | 111,800 | 2.17ms | 2.72ms |
| `insights_overview` | 111,800 | 2.28ms | 2.86ms |
| `task_create` | 47,583 | 7.20ms | 9.42ms |
| `task_update` | 47,583 | 5.96ms | 6.70ms |
| `task_complete` | 47,583 | 6.00ms | 6.80ms |

이 테스트로 얻은 것:

- `recent-completions` 500은 재발하지 않았다.
- 전체 실패율은 `0%`, checks는 `100%`였다.
- projection 변경으로 latency 회귀도 관찰되지 않았다.
- 부하테스트로 발견한 실제 500을 코드 수정으로 해결하고, 동일 부하로 재검증하는 루프를 완성했다.

이후 추가로 한 코드 점검:

- `/api/insights` 계열의 다른 신규 API에도 같은 lazy loading 문제가 생길 수 있는지 점검했다.
- `recent-completions`, `today-completions`는 이미 DTO projection으로 안전해졌다.
- `dashboard`는 lazy 연관관계를 사용하지 않았다.
- `overview`는 즉시 500 위험은 낮았지만, 내부에서 completion의 task 정보를 읽는 구조라 N+1/장시간 부하 리스크가 남아 있었다.

이후 행동:

- `overview`도 `TaskCompletionInsightRow` projection 기반으로 선제 리팩터링했다.

### 6. `overview` Projection 리팩터링

`GET /api/insights/overview`는 실패가 재현된 endpoint는 아니었다. 하지만 테스트 결과를 해석하는 과정에서, 인사이트 조회 API들이 장시간 부하에서 자주 호출된다는 점이 확인되었다.

그래서 `overview`도 다음 방향으로 정리했다.

수정 전:

```text
TaskCompletion 엔티티 조회
-> completion.getTask().getId()
-> completion.getTask().getName()
-> 트랜잭션 내부 lazy loading
```

수정 후:

```text
TaskCompletion + Task join
-> TaskCompletionInsightRow projection 조회
-> taskId, taskName, completedAt, previousDueAt만 사용
```

얻은 것:

- `overview` 계산에서 lazy 연관관계 접근을 제거했다.
- completion 수가 늘어날 때 생길 수 있는 N+1 가능성을 줄였다.
- 이후 fixed-token soak에서 `insights_overview` p95 `3.06ms`, p99 `4.40ms`로 안정적인 것을 확인했다.

### 7. 관측성 강화

500 원인을 찾는 과정에서 Grafana에는 500이 남아 있지만, Docker 로그가 이미 사라져 원인을 확정하기 어려운 상황이 있었다.

이 문제를 해결하기 위해 부하테스트 종료 직후 결과 폴더에 원인 분석 자료를 자동 저장하도록 개선했다.

저장하는 항목:

- API/DB Docker 로그
- 5xx/500 access log
- 401/429 access log
- 5xx requestId 목록
- requestId별 trace
- exception 요약
- DB error 후보
- `500-summary.md`

얻은 것:

- 다음에 500이 발생하면 Grafana 수치만 보고 추정하지 않아도 된다.
- 결과 폴더 안에서 endpoint, requestId, stack trace, DB error 후보를 바로 확인할 수 있다.
- 부하테스트가 성능 측정뿐 아니라 장애 분석 자료 수집까지 포함하게 되었다.

### 8. Fixed Token Soak

- 결과 문서: [local-soak-fixed-token-20260531-022322](../infra/load/results/local-soak-fixed-token-20260531-022322/README.md)
- 목적: projection 수정 이후 장시간 steady load에서 안정성이 유지되는지 확인
- 방식: `60 VU`, `2h`, `85%` read, `15%` write
- 인증 조건: fixed access token, `RELOGIN_ON_401=false`

| Metric | Value |
| --- | ---: |
| Requests | 3,507,776 |
| Avg RPS | 487.14 |
| p95 | 4.08ms |
| p99 | 6.09ms |
| Max | 1,436.87ms |
| HTTP fail | 0% |
| Checks | 100% |

핵심 endpoint 결과:

| Endpoint | Requests | p95 | p99 |
| --- | ---: | ---: | ---: |
| `insights_dashboard` | 455,172 | 3.05ms | 4.39ms |
| `insights_overview` | 455,172 | 3.06ms | 4.40ms |
| `insights_recent` | 455,172 | 2.81ms | 4.04ms |
| `task_create` | 80,393 | 8.18ms | 9.83ms |
| `task_update` | 80,393 | 5.94ms | 6.76ms |
| `task_complete` | 80,393 | 5.99ms | 6.95ms |

로그 추출 결과:

| Item | Count |
| --- | ---: |
| 5xx access lines | 0 |
| 500 access lines | 0 |
| 401/429 access lines | 0 |
| 5xx request IDs | 0 |
| DB error lines | 0 |

이 테스트로 얻은 것:

- 2시간 동안 평균 `487.14 RPS`를 유지하며 실패 없이 처리했다.
- `recent-completions` 500은 장시간 테스트에서도 재발하지 않았다.
- `overview` projection 리팩터링 이후에도 인사이트 API가 안정적인 latency를 유지했다.
- fixed token 조건에서는 인증/rate-limit/서버 내부 예외가 재발하지 않았다.

## 부하테스트를 통해 실제로 바뀐 것

이번 부하테스트를 통해 바뀐 것은 크게 네 가지다.

### 1. 안정성 판단 기준이 바뀜

초기에는 p95 latency와 RPS를 중심으로 결과를 볼 수 있었다. 하지만 mixed 테스트에서 p95가 낮아도 실패율이 매우 높은 상황을 확인했다.

이후 안정성 판단 기준을 다음처럼 바꿨다.

- RPS만 보지 않는다.
- p95만 보지 않는다.
- `http_req_failed`, checks, 상태코드 분포를 함께 본다.
- 5xx는 비율이 낮아도 서버 내부 예외이므로 별도 수정 대상으로 본다.

### 2. 인증 병목과 API 본체를 분리함

초기 mixed/soak에서는 `401`, `429`, `500`이 동시에 발생했다. fixed token 테스트를 통해 대량 실패의 중심이 인증 재로그인/rate-limit 경합임을 분리했다.

그 결과 API 본체는 목표 부하에서 안정적으로 동작한다는 판단을 할 수 있었고, 인증 안정화는 별도 테스트 주제로 분리했다.

### 3. Lazy loading 기반 500을 코드로 수정함

`recent-completions` 500은 실제 stack trace로 원인을 확인한 뒤 projection 조회로 수정했다.

수정한 API:

- `GET /api/insights/recent-completions`
- `GET /api/insights/today-completions`

수정 효과:

- lazy loading 경합 제거
- 삭제된 Task proxy 재조회 문제 제거
- 동일 mixed 부하에서 실패율 `0%`, checks `100%` 확인

### 4. `overview` 조회도 선제 리팩터링함

`GET /api/insights/overview`는 부하테스트에서 직접 500을 낸 endpoint는 아니었다. 하지만 같은 인사이트 영역에서 완료 이력과 task 정보를 함께 다루고 있었고, 장시간 부하에서 호출 빈도가 높았다.

그래서 `TaskCompletionInsightRow` projection으로 변경해 lazy 접근과 N+1 가능성을 줄였다.

### 5. 부하테스트 관측성이 좋아짐

500 원인 추적 과정에서 로그 보존의 중요성을 확인했고, 이후 테스트 종료 시점에 로그 추출을 자동화했다.

이제 부하테스트 결과 폴더는 단순히 k6 summary만 담는 곳이 아니라, 실패가 발생했을 때 원인을 추적할 수 있는 자료까지 함께 보관하는 구조가 되었다.

## 현재 기준 판단

현재까지 검증한 범위에서는 다음을 말할 수 있다.

- read API는 `80 VU`, 평균 `553 RPS` 수준에서 실패 없이 처리했다.
- fixed-token mixed API는 `50 VU`, 평균 `463 RPS`, peak 약 `600 RPS` 수준에서 실패 없이 처리했다.
- fixed-token soak는 `60 VU`, `2h`, 평균 `487 RPS`, 총 `350만+` 요청을 실패 없이 처리했다.
- 부하테스트로 발견한 `recent-completions` 500은 원인 확정, 코드 수정, 재검증까지 완료했다.
- `overview`는 직접 장애가 난 뒤 고친 것이 아니라, 같은 계열의 리스크를 발견하고 선제적으로 projection 리팩터링했다.
- 5xx가 다시 발생할 경우 requestId와 trace를 결과 폴더에서 바로 확인할 수 있는 기반을 마련했다.

따라서 현재 결론은 다음과 같다.

> API 본체와 DB 접근 패턴은 현재 목표한 로컬 홈서버 부하 범위에서 안정적으로 동작한다. 특히 부하테스트가 실제 코드 수정과 재검증으로 이어졌기 때문에, 단순 성능 측정보다 운영 안정성 개선에 가까운 결과를 얻었다.

## 한계와 이후 할 일

이번 결과는 API 본체와 DB 접근 패턴의 안정성을 확인하는 데 의미가 있지만, 운영 환경 전체를 모두 검증한 것은 아니다. 남은 작업은 다음 순서로 정리할 수 있다.

1. 인증 안정화 테스트
   - fixed-token 테스트는 API 본체를 보기 위해 로그인/refresh/token 만료/rate-limit 변수를 의도적으로 제거한 조건이다.
   - 따라서 계정 pool, token 만료 시점 분산, relogin backoff/jitter, 실패 cool-off를 포함한 인증 안정화 테스트가 별도로 필요하다.
   - 목표는 정상적인 갱신 흐름에서 `401/429`가 장기 누적되지 않고, 로그인/refresh 실패가 API 본체 요청 실패로 연쇄 확산되지 않는지 확인하는 것이다.

2. JVM/DB 관측 패널 추가
   - 최신 soak에서 API 컨테이너 메모리가 before/after snapshot 기준으로 증가했다.
   - 현재 snapshot만으로는 정상적인 JVM heap 확장인지, 누수 가능성인지 판단하기 어렵다.
   - Grafana에 JVM heap used/committed/max, GC pause, DB connection pool, slow query 후보, process memory RSS를 추가하면 장시간 부하의 리소스 변화를 더 정확히 볼 수 있다.

3. 더 긴 fixed-token soak
   - 현재 최종 검증은 `60 VU`, `2h` 조건이다.
   - 관측 패널을 추가한 뒤 `4~6h` 또는 야간 soak를 실행하면 메모리 증가가 안정화되는지, p95/p99 latency drift가 없는지 더 명확히 판단할 수 있다.
   - 목표 기준은 실패율 `0%`, checks `100%`, 5xx/500 `0건`, 장시간 latency drift 없음, heap/GC/DB connection 안정화다.

## 관련 문서

- Read Matrix 결과: [infra/load/results/local-read-matrix-20260512-102647/README.md](../infra/load/results/local-read-matrix-20260512-102647/README.md)
- Mixed Peak 결과: [infra/load/results/local-mixed-peak-20260517-043551/README.md](../infra/load/results/local-mixed-peak-20260517-043551/README.md)
- 초기 Soak 결과: [infra/load/results/local-soak-20260517-055109/README.md](../infra/load/results/local-soak-20260517-055109/README.md)
- Fixed Token Mixed 결과: [infra/load/results/local-mixed-fixed-token-20260530-121407/README.md](../infra/load/results/local-mixed-fixed-token-20260530-121407/README.md)
- 500 원인 분석: [infra/load/results/local-mixed-fixed-token-20260530-121407/500-root-cause-analysis.md](../infra/load/results/local-mixed-fixed-token-20260530-121407/500-root-cause-analysis.md)
- Fixed Token Mixed 재검증: [infra/load/results/local-mixed-fixed-token-20260531-005152/README.md](../infra/load/results/local-mixed-fixed-token-20260531-005152/README.md)
- Fixed Token Soak 결과: [infra/load/results/local-soak-fixed-token-20260531-022322/README.md](../infra/load/results/local-soak-fixed-token-20260531-022322/README.md)
