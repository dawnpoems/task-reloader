# Task Reloader

완료 시점을 기준으로 다음 일정을 다시 계산하는 반복 작업 관리 서비스입니다.

```text
next_due_at = completed_at + every_n_days
```

고정 요일/날짜 중심 스케줄러가 놓치기 쉬운 문제, 즉 “실제로 언제 했는가”를 제품의 중심 기준으로 다룹니다.

## 개요

- 완료 시점 기반 반복 작업 모델(`next_due_at = completed_at + every_n_days`)
- 오늘 해야 할 일(`DUE_NOW`) 우선 화면
- 완료 이력(월별/날짜별) 조회
- 운영 관측성(health, requestId, metrics, prometheus) 포함
- 접근성/실패 UX(모달 포커스 관리, Esc/포커스 트랩, 처리 중 상태/재시도) 반영
- 로컬 품질 게이트(type-check/test/build/lint) 기반으로 변경 안정성 관리

## 기술 스택

- Backend: Java 17, Spring Boot, Spring Data JPA, Flyway, PostgreSQL
- Frontend: React, TypeScript, Vite
- Infra/Observability: Docker Compose, Spring Actuator, Micrometer, Prometheus
- Test/Quality: JUnit5, Mockito, Testcontainers, ESLint, TypeScript type-check

## 프로젝트 문제의식과 해결

### 문제의식 (왜 이 프로젝트를 만들었는가)

- 고정 캘린더 기반 반복 일정은 실제 완료 시점과 어긋나기 쉬워 일정 신뢰도가 떨어집니다.
- 당장 해야 할 일과 미래 일정이 섞여 보이면 사용자가 우선순위를 매번 수동으로 판단해야 합니다.
- “마지막 완료 시점”만 보관하면 작업 패턴 변화(언제 밀렸는지, 특정 월에 몰렸는지)를 해석하기 어렵습니다.
- 로컬에서 잘 동작해도 운영 단계에서 요청 추적/지연 분석이 안 되면 장애 대응 속도가 급격히 떨어집니다.

### 제품 수준 해결방안 (무엇을 어떻게 해결했는가)

- 일정 기준을 고정 요일이 아니라 완료 시점으로 전환
- 메인 화면을 `DUE_NOW` 중심으로 구성해 실행 우선순위를 즉시 보이도록 설계
- 완료 이력을 별도 저장하고, 월별/날짜별 탐색 UI로 “작업 흐름”을 확인 가능하게 확장
- 요청 단위 추적(requestId), health probe, metrics/prometheus를 포함해 운영 가능한 형태로 구성
- 모달 접근성(열림/닫힘 포커스 복귀, Esc 닫기, Tab 포커스 트랩)과 실패 UX(원인+행동 메시지, 재시도)를 기본 품질로 적용

## 실행 방법

### 운영 실행 (Docker Compose)

운영 형태는 compose 한 번으로 전체 실행됩니다.

`infra/.env` 최소 예시:

```env
POSTGRES_USER=task_reloader
POSTGRES_PASSWORD=change_me_in_production
POSTGRES_DB=task_reloader
SPRING_DATASOURCE_USERNAME=task_reloader
SPRING_DATASOURCE_PASSWORD=change_me_in_production
```

```sh
cd infra
docker compose up -d
```

- Web: `http://localhost:3000`
- API: `http://localhost:8080`

### 개발 실행 (DB Docker + 백/프론트 로컬)

DB는 Docker로 띄우고, 백엔드/프론트는 로컬 개발 서버로 실행합니다.

1. DB 실행

```sh
cd infra
docker compose up -d postgres
```

2. API 실행

```sh
./gradlew :apps:api:bootRun --args='--spring.profiles.active=local'
```

3. Web 실행

```sh
cd apps/web
npm install
npm run dev
```

- Web: `http://localhost:5173`
- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`

## 기술 설계와 선택 근거 (Trade-offs)

아래 결정은 “도메인 정합성 > 단기 구현 편의”, “운영 추적 가능성 > 단순 로그 출력”을 기준으로 판단했습니다.

### 대안 비교 요약

| 결정 포인트 | 검토한 안 | 최종 선택 | 선택 이유 |
|-------------|----------|----------|----------|
| 상태 모델 | 저장형 상태(enum/status), 배치 선반영, 계산형 | 계산형(`next_due_at`) | 상태 불일치/동기화 비용 최소화 |
| 완료 안정성 | 단순 성공 의존, 낙관적 락만 사용, row lock+쿨다운 | row lock + 쿨다운 | 동시성/연타 상황 데이터 신뢰성 확보 |
| 완료 이력 | 최신값만 유지, 집계 전용 테이블, 이벤트 누적 | `task_completions` 누적 | 월별 조회/통계/감사 추적 확장성 확보 |
| 접근성/실패 UX | 시각 중심 UI, 최소 에러 문구, 접근성 사후 보완 | 접근성/실패 상황을 초기 설계에 포함 | 실제 사용성 품질과 신뢰도 확보 |
| 프론트 조회 전략 | 초기 전체 선조회, 폴링 재조회, 라우트 기반 지연 조회 | 라우트 기반/지연 조회 | 불필요 호출과 렌더 비용 절감 |
| 로깅 구조 | 서비스 직접 로그, AOP 단일 로그, 이벤트 로깅 | 이벤트 발행 + 리스너 | 도메인/운영 관심사 분리, 확장성 확보 |
| 품질 게이트 | 수동 확인 중심, 기능 구현 후 ad-hoc 테스트 | 반복 가능한 체크 루틴 + CI | 회귀 리스크 축소, 협업/배포 안정성 강화 |

### 1) 상태 모델: 저장보다 계산 중심

검토한 안:
  - `DUE_NOW` 포함 상태를 DB enum으로 저장
  - 상태 변경 시 배치/스케줄러로 상태를 선반영
  - `next_due_at` 단일 값으로 조회 시점에 상태 계산

최종 선택:
  - 상태를 DB에 중복 저장하지 않고 `next_due_at` 기준으로 `OVERDUE/TODAY/UPCOMING` 계산
  - `DUE_NOW`는 저장 enum이 아니라 조회 필터(`TODAY + OVERDUE`)로 제공

선택 이유:
  - 시간 경계 변경 시 동기화/배치 복잡도를 줄이고 상태 불일치 리스크를 낮출 수 있음

포기한 점(트레이드오프):
  - 조회 시 계산 비용이 들지만, 상태 정합성 유지 비용보다 작다고 판단

### 2) 완료 처리 안정성: 동시성/중복 방어

검토한 안:
  - 단순 API 호출 성공에만 의존
  - 낙관적 락(version)만 사용하고 중복 요청은 프론트에만 의존
  - row lock + 쿨다운을 서버에서 함께 처리

최종 선택:
  - `findByIdForUpdate` 기반 row lock
  - 2초 중복 완료 쿨다운

선택 이유:
  - 더블클릭/동시 요청 시 이력 중복과 상태 꼬임을 사전에 차단

포기한 점(트레이드오프):
  - 구현 복잡도가 증가하지만 데이터 신뢰성을 우선

### 3) 이력 모델: 최신값만이 아니라 이벤트 누적

검토한 안:
  - `tasks` 테이블에 마지막 완료 정보만 유지
  - 월 집계 테이블을 별도로 두고 원본 이력은 저장하지 않음
  - 완료 이벤트를 별도 테이블에 누적 저장

최종 선택:
  - `task_completions` 테이블에 완료 이벤트를 누적 저장

선택 이유:
  - 월별 캘린더, 날짜별 이력, 향후 통계/패턴 분석까지 자연스럽게 확장 가능

포기한 점(트레이드오프):
  - 저장량과 조회 쿼리가 늘어나지만, 기능 확장 비용을 크게 줄일 수 있음

### 4) 접근성/실패 UX: 기능이 아니라 기본 품질

검토한 안:
  - 기능 우선 구현 후 접근성/에러 처리는 추후 보완
  - 단일 전역 에러 배너로 일괄 처리
  - 로딩/처리중 상태를 UI에서 최소 표시

최종 선택:
  - 모달에 `role="dialog"`, `aria-modal`, 제목 연결, Esc 닫기, 포커스 트랩/복귀 적용
  - 버튼 처리 상태(`처리 중...`)와 중복 클릭 방지(disabled)를 주요 액션에 반영
  - 오류 메시지를 “원인 + 다음 행동(재시도/뒤로)” 형태로 통일하고, 전역/로컬 에러 노출 범위 분리

선택 이유:
  - 사용자는 성공 흐름보다 실패/지연 상황에서 품질 차이를 더 크게 체감함
  - 포트폴리오 관점에서도 “실패 상황을 설계했다”는 신뢰를 줄 수 있음

포기한 점(트레이드오프):
  - 초기 구현 속도는 다소 느려지지만, 이후 수정 비용과 사용자 혼란을 줄일 수 있음

### 5) 프론트 데이터 흐름: 라우트 기반 최소 호출

검토한 안:
  - 앱 초기 로드에서 모든 화면 데이터 선조회
  - 폴링 방식으로 정기 전체 재조회
  - 라우트별 필요 데이터만 요청하고 나머지는 지연 조회

최종 선택:
  - 인사이트 API는 인사이트 페이지에서만 로드(`enabled`)
  - `UPCOMING`은 펼칠 때 지연 조회

선택 이유:
  - 홈/상세 중심 사용 시 불필요한 네트워크 호출과 렌더 비용 절감

포기한 점(트레이드오프):
  - 상태 전이 로직이 조금 복잡해지지만 체감 성능과 비용 효율 개선

### 6) 운영 추적: requestId + metrics + 이벤트 로깅

검토한 안:
  - 서비스 메서드 내부에서 직접 로그 + 최소 health만 노출
  - AOP 단일 포인트 로그만 사용(도메인 이벤트 미사용)
  - requestId + metrics 중심의 관측성만 적용하고 이벤트 로깅은 생략

최종 선택:
  - `X-Request-Id` 생성/전파, access log(latency 포함), 에러 응답 requestId 연계
  - actuator health/readiness/liveness + metrics + prometheus 노출
  - 서비스 직접 로그 대신 도메인 이벤트 발행 + 리스너(`AFTER_COMMIT`) 로깅

선택 이유:
  - 장애 분석 시 요청 단위 추적을 통일하고, 비즈니스 로직과 운영 관심사를 분리

포기한 점(트레이드오프):
  - 이벤트/리스너 관리 비용이 생기지만, 로깅 정책 확장과 유지보수가 쉬워짐

### 7) 품질 게이트: 빠른 개발과 안정성의 균형

검토한 안:
  - 기능 개발 후 수동 점검만 수행
  - 배포/머지 시점에만 테스트 수행

최종 선택:
  - 로컬 루틴(`type-check`, `test`, `build`, `lint`)을 고정해 반복 검증
  - GitHub 체크를 통해 머지 전 품질 게이트를 적용할 수 있는 구조 마련

선택 이유:
  - 1인 개발이어도 루틴화된 검증은 회귀를 줄이고 리팩터링 속도를 높임
  - “개발 편의”와 “기본 품질”을 동시에 유지할 수 있음

포기한 점(트레이드오프):
  - 초기 설정/유지 비용이 들지만, 장기적으로 디버깅 시간을 크게 절약

## 결과

### 사용자 관점

- “오늘 할 일” 중심으로 우선순위 판단 시간이 줄어듦
- 완료 이력을 월/날짜 단위로 확인해 작업 리듬을 해석 가능
- 완료 시 피드백과 중복 처리 방어로 상호작용 안정성 향상
- 모달/버튼/에러 흐름 개선으로 키보드 사용자와 실패 상황 사용자 경험 품질 향상

### 기술/운영 관점

- 상태 계산 일원화로 모델 정합성 강화
- 완료 처리 동시성 방어 + 이력 저장으로 데이터 신뢰성 강화
- requestId + metrics + health 구성으로 “동작함”에서 “운영 가능함”으로 확장
- 이벤트 기반 로깅 분리로 서비스 코드 응집도 유지 및 운영 정책 확장성 확보
- 라우트 기반 데이터 로딩으로 불필요 API 호출 감소(특히 인사이트/상세 흐름)

### 확인 포인트

- Task 완료 후 `next_due_at`이 완료 시각 기준으로 갱신되는지
- 상세 화면 월별/날짜별 이력이 저장 데이터와 일치하는지
- 같은 요청의 requestId가 응답/로그/에러에 연결되는지
- `/actuator/metrics/http.server.requests`에서 URI별 요청량/지연시간이 관측되는지
- 모달 열기/닫기 시 포커스 이동·복귀, Esc 닫기, Tab 순환이 정상 동작하는지
- 실패 시 전역 에러와 로컬 폼 에러가 과도하게 중복되지 않는지, 재시도 동선이 제공되는지

### 한계와 다음 단계

- 프론트 네트워크 최적화는 계속 진행 중(개발 모드 StrictMode 영향 포함)
- 지표 노출은 완료했고, Grafana 대시보드 템플릿 정리는 다음 단계
- 실패 UX(원인+행동 안내) 고도화 여지 있음

## 주요 엔드포인트 요약

Base URL: `/api`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/tasks?status=DUE_NOW` | 지금 할 일(OVERDUE + TODAY) 조회 |
| `POST` | `/tasks` | Task 생성 |
| `PATCH` | `/tasks/{id}` | Task 수정 |
| `POST` | `/tasks/{id}/complete` | Task 완료 처리 |
| `GET` | `/tasks/{id}/completions` | 완료 이력 조회 |
| `GET` | `/tasks/{id}/completions?year=YYYY&month=MM` | 월별 완료 이력 조회 |

## 관측성 엔드포인트

- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Prometheus: `/actuator/prometheus`

`http.server.requests`로 요청량(count), 오류율(status), 응답시간(latency)을 추적할 수 있습니다.

## 확장 계획

1. 인사이트 고도화: 완료율/지연률/작업별 추세를 추가해 “기록”을 “의사결정 정보”로 전환 
2. Grafana 대시보드: Prometheus 지표를 요청량/에러율/p95 latency 중심으로 시각화해 운영 추적성 강화 
3. 멀티유저 지원: 사용자 계정과 권한 모델을 도입해 개인별 작업 관리와 협업 기능 확장
4. CloudFlare Tunnel을 활용한 외부 개방 : 서버를 안전하게 외부에 노출해 실사용자 테스트와 피드백 수집 용이성 개선 
5. 부하 테스트 : k6 등의 도구로 동시 요청 시 시스템 안정성과 병목 구간을 분석해 최적화 포인트 도출 
6. 알림 시스템 MVP: `DUE_NOW` 발생 시 이메일 알림을 보내 실제 행동 유도 
7. Grace Window 도입: due 직후 짧은 유예 구간을 두어 과도한 overdue 판정을 줄이고 사용자 신뢰도 개선 

