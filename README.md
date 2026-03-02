# task-reloader
A completion-driven recurring task scheduler

### 프로젝트명

**Task Reloader**

### 한 줄 설명

완료 시점 기준으로 반복 작업을 다시 스케줄링하는 **completion-driven recurring task scheduler**

### 문제의식

- 반복 작업(ToDo/점검/집안일/운영 작업)은 “매주 월요일” 같은 **고정 날짜 기준**으로 관리하면 항상 밀린다.
- 실제로 중요한 건 **언제 했는가**이며, 다음 작업은 **완료 시점 기준**으로 계산되는 게 더 현실적이다.

### 해결 방식

- Task를 반복 주기(DAY)와 함께 등록
- 사용자가 완료(complete)하면 **완료 시점 + 주기**로 다음 due를 재계산
- 전체 Task를 **상태(밀림/오늘/남음)** 기준으로 한눈에 보여줌

---

## 2. MVP 범위

### 포함

- Task 등록 (반복 단위: DAY)
- Task 완료 처리 (completion-driven rescheduling)
- Task 목록 조회 (상태 포함)
    - OVERDUE (밀린 것)
    - TODAY (오늘 할 것)
    - UPCOMING (남은 것)
- Task 수정
    - 이름 변경
    - 반복 주기 변경
    - 활성/비활성(is_active)
- Docker Compose 기반 로컬/홈서버 실행

### 제외 (v2 이후)

- 로그인/권한(JWT)
- 다중 사용자
- WEEK / MONTH / HOUR 단위
- 알림(Slack/Email)
- Task 인스턴스/감사 로그 테이블
- Testcontainers

---

## 3. 사용자 플로우

1. 사용자가 Task를 등록

   → `next_due_at = now` 로 설정되어 즉시 “오늘 할 일”에 표시됨

2. 사용자가 Task를 완료 처리
3. 시스템이 완료 시점을 기준으로 다음 due 계산

   → `next_due_at = completed_at + every_n_days`

4. Task는 자동으로 다음 주기로 “reload”됨
5. 목록 화면에서 상태가 자동 갱신됨

---

## 4. 상태 모델 (Timezone: Asia/Seoul)

### 기준 시각

- `todayStart`: 오늘 00:00
- `tomorrowStart`: 내일 00:00

### 상태 정의

| 상태 | 조건 |
| --- | --- |
| OVERDUE | `next_due_at < todayStart` |
| TODAY | `todayStart ≤ next_due_at < tomorrowStart` |
| UPCOMING | `next_due_at ≥ tomorrowStart` |

### 정렬 규칙

- OVERDUE: 가장 오래 밀린 것부터
- TODAY: due 빠른 순
- UPCOMING: due 빠른 순

---

## 5. 데이터 모델 (MVP 단일 테이블)

### tasks

- `id` (BIGSERIAL, PK)
- `name` (VARCHAR, NOT NULL)
- `every_n_days` (INT, ≥ 1)
- `timezone` (VARCHAR, default `Asia/Seoul`)
- `next_due_at` (TIMESTAMPTZ, NOT NULL) ⭐ 핵심
- `last_completed_at` (TIMESTAMPTZ, nullable)
- `is_active` (BOOLEAN, default true)
- `created_at` (TIMESTAMPTZ)
- `updated_at` (TIMESTAMPTZ)

### 인덱스

- `(is_active, next_due_at)`

**설계 의도**

- Task 인스턴스를 분리하지 않고 `next_due_at`을 **단일 진실(Single Source of Truth)**로 사용
- 단순하지만 completion-based 스케줄링의 핵심을 가장 잘 드러내는 구조

---

## 6. 핵심 비즈니스 로직

### Task 생성

- 입력: name, every_n_days
- 처리:
    - `next_due_at = now`
    - `last_completed_at = null`
    - `is_active = true`

### Task 완료 (Complete)

- `completed_at = now`
- `last_completed_at = completed_at`
- `next_due_at = completed_at + every_n_days`

### 동시성/중복 처리

- 트랜잭션 내부에서 **DB row lock (`SELECT … FOR UPDATE`)** 사용
- 동일 Task에 대한 중복 완료 호출 방지

### 반복 주기 변경

- MVP 정책:
    - 주기 변경 시 **즉시 next_due_at 재계산 없음**
    - 다음 complete부터 새 주기 반영

---

## 7. API 설계 (REST)

Base URL: `/api`

### Task 생성

- `POST /tasks`
- Request: name, everyNdays
- Response: Task + status

### Task 목록 조회

- `GET /tasks?status=OVERDUE|TODAY|UPCOMING|ALL`
- Response: Task 리스트 (status 포함)

### Task 수정

- `PATCH /tasks/{id}`
- 수정 가능 필드:
    - name
    - everyNdays
    - isActive

### Task 완료

- `POST /tasks/{id}/complete`
- Response:
    - id
    - lastCompletedAt
    - nextDueAt
    - status

---

## 8. Frontend (간단 React)

### 기술

- React
- Vite
- TypeScript

### 화면 구성 (Single Page)

- 상단: Task 추가 폼 (name, every_n_days)
- 하단: 3개 섹션
    - Overdue
    - Today
    - Upcoming
- 각 Task:
    - name / next_due_at 표시
    - Complete 버튼
    - Pause/Resume 토글

---

## 9. 기술 스택

### Backend

- Java 17
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Spring Validation
- Flyway Migration
- springdoc-openapi (Swagger)

### Database

- PostgreSQL 16 (개발/운영)
- H2 (테스트)

### DevOps

- Docker
- Docker Compose (api + postgres)

### Test

- Spring Boot Test
- JUnit 5
- AssertJ
- (MVP에서는 Testcontainers 미사용)

---

## 10. Project Metadata (Spring Initializr)

- **Group**: `io.github.yegeunkim`
- **Artifact**: `task-reloader-api`
- **Name**: `task-reloader-api`
- **Description**: `Completion-driven recurring task scheduler API`
- **Package name**: `io.github.yegeunkim.taskreloader`
- **Packaging**: Jar
- **Java**: 17
- **Version**: 0.1.0-SNAPSHOT

---

## 11. Repository 구조

```
task-reloader/
├─ apps/
│  ├─ api/        # Spring Boot (task-reloader-api)
│  └─ web/        # React (Vite)
├─ infra/
│  └─ docker-compose.yml
└─ README.md
```

---

## 12. 확장 계획 (v2)

- JWT 기반 인증/권한
- 사용자별 Task
- 주기 단위 확장 (WEEK / MONTH)
- Grace window / verification
- 알림(Slack/Email)
- Task 인스턴스 + audit log
- Testcontainers(Postgres)
- CI(GitHub Actions)