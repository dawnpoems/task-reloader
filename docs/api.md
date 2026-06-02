# API Reference

이 문서는 Task Reloader의 주요 API와 인사이트 지표 정의를 정리한다.

Base URL: `/api` (`/healthz`, `/actuator/**` 제외)

- 공개/인증 예외: `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`
- 그 외 API는 bearer access token 필요
- `/api/admin/**`는 `ADMIN` 역할 필요
- `refresh/logout`은 refresh cookie와 CSRF cookie/header 흐름을 사용

## 인증/사용자

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/auth/signup` | 회원가입. 신규 사용자는 승인 대기(`PENDING`) 상태로 생성 |
| `POST` | `/auth/login` | 로그인. access token 반환, refresh/CSRF cookie 설정 |
| `POST` | `/auth/refresh` | refresh token 회전 후 access token 재발급 |
| `POST` | `/auth/logout` | refresh token 폐기 및 인증 cookie 제거 |
| `GET` | `/auth/me` | 현재 로그인 사용자 정보 조회 |

## Task

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/tasks?status=ALL` | 활성 Task 전체 조회 |
| `GET` | `/tasks?status=DUE_NOW` | 지금 할 일 조회(`OVERDUE + TODAY`) |
| `GET` | `/tasks?status=OVERDUE` | 계산된 상태별 Task 조회. `TODAY`, `UPCOMING`도 지원 |
| `GET` | `/tasks/{id}` | Task 단건 조회 |
| `POST` | `/tasks` | Task 생성 |
| `PATCH` | `/tasks/{id}` | Task 수정 |
| `DELETE` | `/tasks/{id}` | Task 삭제 |
| `POST` | `/tasks/{id}/complete` | Task 완료 처리. row lock + 2초 쿨다운으로 중복 완료 방어 |
| `GET` | `/tasks/{id}/completions` | Task 완료 이력 전체 조회 |
| `GET` | `/tasks/{id}/completions?year=YYYY&month=M` | KST 월 경계 기준 완료 이력 조회 |

## 인사이트

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/insights/dashboard` | 전체/기한초과/오늘/예정/오늘 완료/최근 7일 완료 요약 |
| `GET` | `/insights/overview?days=30&top=5` | 완료율, 지연률, 평균 지연일, 위험 작업, 완료/지연 Top 추세 조회 |
| `GET` | `/insights/recent-completions` | 최근 완료 작업 5건 조회 |
| `GET` | `/insights/today-completions` | KST 오늘 완료한 작업 조회 |

## 이메일 알림

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/alerts/task-due-email/settings` | 작업 마감 이메일 알림 설정과 최근 발송 결과 조회 |
| `PATCH` | `/alerts/task-due-email/settings` | 알림 활성화, 발송 시각, timezone 설정 수정 |
| `GET` | `/alerts/task-due-email/recipients` | 알림 수신자 목록 조회 |
| `POST` | `/alerts/task-due-email/recipients` | 알림 수신자 추가 |
| `DELETE` | `/alerts/task-due-email/recipients/{id}` | 알림 수신자 삭제 |
| `POST` | `/alerts/task-due-email/test-send` | 로컬 프로필 전용 즉시 발송 테스트 |

## 관리자

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/admin/users/pending` | 승인 대기 사용자 목록 조회 |
| `GET` | `/admin/users/non-pending` | 승인/거절 사용자 목록 조회 |
| `POST` | `/admin/users/{userId}/approve` | 승인 대기 사용자 승인 |
| `POST` | `/admin/users/{userId}/reject` | 승인 대기 사용자 거절 |
| `PATCH` | `/admin/users/{userId}/status` | 승인/거절 사용자 상태 전환 |

## 인사이트 지표 정의

### `/api/insights/dashboard`

| 필드 | 정의 |
|------|------|
| `totalTasks` | 현재 사용자의 활성 Task 수 |
| `overdueTasks` | KST 오늘 시작보다 `next_due_at`이 이전인 활성 Task 수 |
| `todayTasks` | KST 오늘 범위에 `next_due_at`이 포함된 활성 Task 수 |
| `upcomingTasks` | KST 내일 시작 이후 예정된 활성 Task 수 |
| `completedToday` | KST 오늘 완료한 이력 수 |
| `completedLast7Days` | 현재 시각 기준 최근 7일 완료 이력 수 |

### `/api/insights/overview?days=30&top=5`

- 파라미터 제한: `days`는 `1~365`, `top`은 `1~20`
- 기간: `periodStart <= completed_at < periodEnd` (`periodStart = now - days`, `periodEnd = now`, UTC 응답 + `timezone=Asia/Seoul`)

| 필드 | 정의 |
|------|------|
| `activeTaskCount` | 현재 활성 Task 수 |
| `completedTaskCount` | 기간 내 1회 이상 완료한 활성 Task 수 |
| `completionCount` | 기간 내 활성 Task 완료 이력 수 |
| `delayedCompletionCount` | `completed_at > previous_due_at`인 완료 이력 수 |
| `completionRatePct` | `completedTaskCount / activeTaskCount * 100` (소수점 1자리) |
| `delayRatePct` | `delayedCompletionCount / completionCount * 100` (소수점 1자리) |
| `averageDelayDays` | 지연 완료 건의 `completed_at - previous_due_at` 평균 일수 (소수점 2자리) |
| `riskyTaskCount` | `riskyTasks` 개수 |
| `topCompletionTrends` | 완료 건수 내림차순 Top N |
| `topDelayedTrends` | 지연 완료 건수 내림차순 Top N |
| `topDelayRateTrends` | 지연률 내림차순 Top N |
| `taskTrends` | 하위 호환용 필드. 현재는 `topCompletionTrends`와 동일 |

### 위험 작업 사유

| reason | 정의 |
|--------|------|
| `OVERDUE_7D_PLUS` | `next_due_at`이 현재 시각보다 7일 이상 지남 |
| `NO_COMPLETION_30D` | 완료 이력이 없거나 마지막 완료가 현재 시각보다 30일 이상 이전 |

## 관측성 엔드포인트

| Path | 설명 |
|------|------|
| `/healthz` | Lightweight health check |
| `/actuator/health` | Spring Actuator health |
| `/actuator/metrics` | Actuator metrics |
| `/actuator/prometheus` | Prometheus scrape endpoint |

`http.server.requests`로 요청량(count), 오류율(status), 응답시간(latency), endpoint별 병목을 추적할 수 있다. API 응답/로그에는 `X-Request-Id`가 연결되어 Grafana 수치에서 실제 requestId와 예외 로그까지 이어서 확인할 수 있다.
