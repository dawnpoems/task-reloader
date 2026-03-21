# Task Reloader

완료한 시점을 기준으로 다음 일정을 다시 잡아주는 반복 작업 관리 서비스입니다.

일반적인 반복 작업 도구가 "매주 월요일"처럼 고정된 날짜를 기준으로 움직인다면, Task Reloader는 "언제 완료했는가"를 기준으로 다음 due date를 계산합니다.

```text
next_due_at = completed_at + every_n_days
```

집안일, 운영 점검, 정기 확인 작업처럼 "마지막으로 실제 수행한 시점"이 중요한 작업에 맞춘 구조입니다.

## 핵심 아이디어

- Completion-driven rescheduling
  작업을 완료하면 그 시점 기준으로 다음 일정이 계산됩니다.
- 단일 기준값 `next_due_at`
  상태를 따로 저장하지 않고, `next_due_at`만으로 OVERDUE / TODAY / UPCOMING을 계산합니다.
- 동시성 방어
  완료 요청에는 DB row lock과 2초 중복 완료 방어를 함께 적용했습니다.

## 주요 기능

- Task 등록
- Task 목록 조회
- Task 수정
- Task 삭제
- Task 완료 처리
- 상태 분류: `OVERDUE`, `TODAY`, `UPCOMING`

## 상태 규칙

기준 타임존은 `Asia/Seoul`입니다.

- `OVERDUE`: `next_due_at < 오늘 00:00`
- `TODAY`: `오늘 00:00 <= next_due_at < 내일 00:00`
- `UPCOMING`: `next_due_at >= 내일 00:00`

## 기술 스택

- Backend: Java 17, Spring Boot, Spring Data JPA, Flyway, PostgreSQL
- Frontend: React, Vite, TypeScript
- Infra: Docker, Docker Compose, nginx
- Test: JUnit 5, Mockito, Testcontainers

## 프로젝트 구조

```text
task-reloader/
├── apps/
│   ├── api/      # Spring Boot API
│   └── web/      # React + Vite frontend
├── infra/
│   └── docker-compose.yml
└── README.md
```

## API 요약

Base URL: `/api`

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/tasks` | Task 생성 |
| `GET` | `/tasks` | Task 목록 조회 |
| `GET` | `/tasks/{id}` | Task 단건 조회 |
| `PATCH` | `/tasks/{id}` | Task 수정 |
| `POST` | `/tasks/{id}/complete` | Task 완료 처리 |
| `DELETE` | `/tasks/{id}` | Task 삭제 |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 운영 관측성(Observability)

- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Prometheus: `/actuator/prometheus`

핵심 지표는 `http.server.requests`를 통해 확인할 수 있습니다.
- 요청량: count
- 에러율: status 태그(4xx/5xx) 기준 비율
- 응답시간: max/percentile/histogram

## 로컬 실행

### 1. DB만 Docker로 실행

```sh
cd infra
docker compose up -d postgres
```

### 2. API 실행

```sh
./gradlew :apps:api:bootRun --args='--spring.profiles.active=local'
```

### 3. Web 실행

```sh
cd apps/web
npm install
npm run dev
```

- Web: `http://localhost:5173`
- API: `http://localhost:8080`

Vite 개발 서버는 `/api` 요청을 API 서버로 프록시합니다.

## Docker 전체 실행

```sh
cd infra
docker compose up --build -d
```

- Web: `http://localhost:3000`
- API: `http://localhost:8080`
- DB: `localhost:5432`

## 테스트

### Backend

```sh
./gradlew :apps:api:test
```

### Frontend

```sh
cd apps/web
npm run type-check
```

## 현재 프로젝트에서 특히 신경 쓴 부분

- 완료 시점 기반 재스케줄링 규칙을 도메인 중심으로 유지
- KST 기준 날짜 경계 계산
- 완료 API의 동시성 처리와 중복 클릭 방어
- 상태를 저장하지 않고 계산으로 유지하는 단순한 모델
- 로컬 개발과 Docker 실행 흐름을 모두 지원하는 구성

## 로깅을 이벤트 기반으로 분리한 이유

서비스 코드(`TaskService`)에 로그를 직접 많이 남기면, 시간이 갈수록 비즈니스 로직과 운영 코드가 섞여 가독성과 유지보수성이 떨어집니다.  
이 프로젝트는 `TaskCreated/Updated/Deleted/Completed` 같은 도메인 이벤트를 서비스에서 발행하고, 로그는 별도 리스너에서 처리하도록 분리했습니다.

- 서비스는 "무엇을 수행하는지"에 집중
- 로깅은 "어떻게 관찰할지"를 별도 계층에서 담당
- 트랜잭션 커밋 이후(`AFTER_COMMIT`) 로그 기록으로 실제 반영된 변경 기준 관측
- 요청 단위 `requestId`와 연결해 장애 추적성 강화

결과적으로 기능 변경 시 서비스 코드 오염을 줄이고, 운영 관점(추적/분석) 개선을 독립적으로 확장하기 쉬운 구조를 만들었습니다.
