# Task Reloader

> **Completion-driven recurring task scheduler**
> 완료 시점 기준으로 반복 작업을 자동 재스케줄링하는 서비스

---

## 1. 핵심 차별점

### Completion-driven Rescheduling
일반적인 반복 작업 도구는 "매주 월요일"처럼 **고정 날짜** 기준으로 다음 일정을 잡는다.
Task Reloader는 **완료한 시점**을 기준으로 다음 due를 계산한다.

```
next_due_at = completed_at + every_n_days
```

실제로 집안일, 점검, 운영 작업 같은 반복 작업은 "언제 했는가"가 기준이 되는 게 더 현실적이다.

### 동시성 처리
완료 요청이 동시에 들어와도 데이터가 꼬이지 않도록 두 가지 장치를 적용했다.

1. **DB Row Lock** (`SELECT … FOR UPDATE`): 트랜잭션 안에서 해당 Task 행에 잠금을 걸어, 여러 서버에서 동시에 complete가 들어와도 순차 처리를 보장한다.
2. **Double-click Guard**: `last_completed_at` 기준으로 **2초 이내 재완료 요청은 409로 거부**한다. Row lock만으로는 막기 어려운 연속 클릭을 방어한다.

---

## 2. 기능 범위 (MVP)

| 기능 | 설명 |
|------|------|
| Task 등록 | name, every_n_days |
| Task 완료 | completion-driven rescheduling |
| Task 목록 조회 | 상태 필터 (OVERDUE / TODAY / UPCOMING) |
| Task 수정 | name, every_n_days, is_active |
| 실행 환경 | Docker Compose (api + postgres) |

**v2 이후 범위 (현재 제외)**

- 로그인 / 다중 사용자
- WEEK / MONTH 단위 반복
- 알림 (Slack / Email)
- Task 인스턴스 / audit log

---

## 3. 사용자 플로우

```
1. Task 등록        → next_due_at = now + every_n_days
2. 완료 처리        → completed_at = now
                       last_completed_at = now
                       next_due_at = now + every_n_days
3. 목록 조회        → OVERDUE / TODAY / UPCOMING 상태로 자동 분류
```

---

## 4. 상태 모델

기준 타임존: `Asia/Seoul`

| 상태 | 조건 |
|------|------|
| OVERDUE | `next_due_at < 오늘 00:00` |
| TODAY | `오늘 00:00 ≤ next_due_at < 내일 00:00` |
| UPCOMING | `next_due_at ≥ 내일 00:00` |

각 상태 내 정렬: `next_due_at ASC` (오래된 것부터)

---

## 5. 데이터 모델

### tasks 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGSERIAL PK | |
| `name` | VARCHAR NOT NULL | |
| `every_n_days` | INT ≥ 1 | 반복 주기 |
| `timezone` | VARCHAR | default `Asia/Seoul` |
| `next_due_at` | TIMESTAMPTZ NOT NULL | ⭐ 핵심 — 단일 진실(SSoT) |
| `completed_at` | TIMESTAMPTZ NULL | 가장 최근 완료 시각 |
| `last_completed_at` | TIMESTAMPTZ NULL | double-click guard 기준 |
| `is_active` | BOOLEAN | default true |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |

인덱스: `(is_active, next_due_at)`

---

## 6. API

Base URL: `/api`

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/tasks` | Task 생성 |
| `GET` | `/tasks?status=ALL\|OVERDUE\|TODAY\|UPCOMING` | 목록 조회 |
| `GET` | `/tasks/{id}` | 단건 조회 |
| `PATCH` | `/tasks/{id}` | 수정 |
| `POST` | `/tasks/{id}/complete` | 완료 처리 |
| `DELETE` | `/tasks/{id}` | 삭제 |

### 완료 API 응답 코드

| 코드 | 상황 |
|------|------|
| 200 | 정상 완료 |
| 404 | Task 없음 |
| 409 `TASK_INACTIVE` | 비활성 Task |
| 409 `TASK_ALREADY_COMPLETED_RECENTLY` | 2초 이내 중복 요청 |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## 7. 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 4.x, Spring Data JPA, Flyway |
| Database | PostgreSQL 16 |
| API 문서 | springdoc-openapi (Swagger) |
| Frontend | React, Vite, TypeScript |
| 인프라 | Docker, Docker Compose |
| 테스트 | JUnit 5, AssertJ, Mockito, Testcontainers |

---

## 8. 로컬 실행

### 사전 준비
- Docker Desktop
- Java 17

### DB만 실행 (API는 호스트에서 직접)

```sh
cd infra
docker compose up -d postgres

cd apps/api
./gradlew bootRun --args='--spring.profiles.active=local'
```

### API + DB 모두 Docker로 실행

```sh
cd infra
docker compose up -d
```

### 동작 확인

```sh
curl http://localhost:8080/healthz
open http://localhost:8080/swagger-ui/index.html
```

### DB 스키마 확인

```sh
psql "postgresql://task_reloader:change_me_in_production@localhost:5432/task_reloader" -c "\dt"
```

---

## 9. 프로젝트 구조

```
task-reloader/
├── apps/
│   ├── api/        # Spring Boot
│   └── web/        # React (Vite)
├── infra/
│   └── docker-compose.yml
└── README.md
```
