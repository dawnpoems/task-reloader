# Task Reloader

완료 시점을 기준으로 다음 일정을 다시 계산하는 반복 작업 관리 서비스입니다.

```text
next_due_at = completed_at + every_n_days
```

고정 요일/날짜 중심 스케줄러가 놓치기 쉬운 문제, 즉 “실제로 언제 했는가”를 제품의 중심 기준으로 다룹니다.

## 라이브 데모

- 서비스 URL: [https://task.dawnpoem.kr](https://task.dawnpoem.kr)
- 포트폴리오 방문자는 위 도메인에서 바로 로그인/체험할 수 있습니다.
- 로그인 화면의 `데모 계정으로 빠르게 체험하기` 버튼으로 데모 계정 안내를 확인할 수 있습니다.
- 일반 가입자는 회원가입 후 관리자 승인 완료 시 사용 가능합니다.
- 운영 보호를 위해 관리자/운영 도구는 공개 도메인과 분리해 접근 제어합니다.

## 개요

- 완료 시점 기반 반복 작업 모델(`next_due_at = completed_at + every_n_days`)
- `next_due_at`과 KST 기준 일 경계로 `OVERDUE/TODAY/UPCOMING` 상태를 계산하고, `DUE_NOW`는 `OVERDUE + TODAY` 조회 필터로 제공
- 회원가입, 관리자 승인, 로그인, access token + refresh cookie 회전, CSRF 보호를 포함한 멀티유저 인증 흐름
- 홈 화면은 지금 할 일(`DUE_NOW`) 우선, 남은 일정(`UPCOMING`)은 펼칠 때 지연 조회
- 상세 화면에서 완료 이력을 월별/날짜별 캘린더로 확인
- 인사이트 화면에서 오늘 완료, 방치 위험 작업, 지연/무완료 신호, 작업별 완료/지연 추세 제공
- 작업 마감 이메일 알림 설정, 수신자 관리, 스케줄러 기반 발송, 로컬 테스트 발송 API 제공
- 운영 관측성(health, requestId, access log, metrics, prometheus, grafana)과 부하 테스트 결과 문서화
- 접근성/실패 UX(모달 포커스 관리, Esc/포커스 트랩, 처리 중 상태/재시도, 429 재시도 카운트다운) 반영
- 로컬 품질 게이트(type-check/test/build/lint)와 API 테스트 기반으로 변경 안정성 관리

## 기술 스택

- Backend: Java 17, Spring Boot, Spring Data JPA, Flyway, PostgreSQL
- Frontend: React, TypeScript, Vite
- Infra/Observability: Docker Compose, Spring Actuator, Micrometer, Prometheus, Grafana
- Test/Quality: JUnit5, Mockito, Testcontainers, ESLint, TypeScript type-check

## 프로젝트 문제의식과 해결

### 문제의식 (왜 이 프로젝트를 만들었는가)

- 고정 캘린더 기반 반복 일정은 실제 완료 시점과 어긋나기 쉬워 일정 신뢰도가 떨어집니다.
- 당장 해야 할 일과 미래 일정이 섞여 보이면 사용자가 우선순위를 매번 수동으로 판단해야 합니다.
- “마지막 완료 시점”만 보관하면 작업 패턴 변화(언제 밀렸는지, 특정 월에 몰렸는지)를 해석하기 어렵습니다.
- 완료 기록이 쌓여도 어떤 작업을 줄이거나 재조정해야 하는지 바로 보이지 않으면 행동으로 이어지기 어렵습니다.
- 공개 데모 서비스에서는 가입/승인/권한/관리 기능을 분리하지 않으면 운영 보호와 사용자 체험을 동시에 만족시키기 어렵습니다.
- 이메일 알림은 단순 발송보다 중복 발송 방지, 수신자 관리, 실패 추적까지 함께 설계해야 실제 운영에 쓸 수 있습니다.
- 로컬에서 잘 동작해도 운영 단계에서 요청 추적/지연 분석이 안 되면 장애 대응 속도가 급격히 떨어집니다.

### 제품 수준 해결방안 (무엇을 어떻게 해결했는가)

- 일정 기준을 고정 요일이 아니라 완료 시점으로 전환
- 메인 화면을 `DUE_NOW` 중심으로 구성해 실행 우선순위를 즉시 보이도록 설계
- 완료 이력을 별도 저장하고, 월별/날짜별 탐색 UI로 “작업 흐름”을 확인 가능하게 확장
- 인사이트 API를 단순 통계가 아니라 오늘 완료, 7일 이상 지연, 30일 무완료, 완료/지연 Top 추세처럼 다음 행동을 정하기 쉬운 신호 중심으로 구성
- 계정 상태(`PENDING/APPROVED/REJECTED`)와 역할(`USER/ADMIN`)을 분리하고, 관리자 승인 화면으로 공개 가입 흐름을 통제
- 작업 마감 이메일 알림을 사용자별 설정/수신자/발송 스케줄/최근 발송 결과 단위로 관리
- 요청 단위 추적(requestId), health probe, metrics/prometheus를 포함해 운영 가능한 형태로 구성
- 모달 접근성(열림/닫힘 포커스 복귀, Esc 닫기, Tab 포커스 트랩)과 실패 UX(원인+행동 메시지, 재시도, rate-limit 안내)를 기본 품질로 적용

## 실행 방법

### 운영 실행 (Docker Compose)

운영 실행, `.env` 설정, Cloudflare Tunnel, 운영 보안 체크리스트는 아래 문서를 단일 기준으로 사용합니다.

- [infra/README.md](infra/README.md)

빠른 시작:

```sh
cp infra/.env.example infra/.env
cd infra
docker compose up -d --build
```

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
| 인증/승인 | 데모 전용 계정만 제공, 단순 JWT, refresh cookie+관리자 승인 | access token + HttpOnly refresh cookie + 관리자 승인 | 공개 데모와 실제 가입 흐름을 함께 보호 |
| 알림 발송 | 화면 알림만 제공, 단발 메일 발송, 사용자별 설정+스케줄러 | 설정/수신자/발송 스케줄 기반 이메일 알림 | 실제 운영 가능한 알림 흐름 확보 |
| 인사이트 계산 | 저장형 요약 테이블, 엔티티 lazy 조회, projection 기반 요청 시 계산 | projection 기반 요청 시 계산 | 현재 규모에서는 정합성과 구현 단순성 확보, N+1/lazy 리스크 완화 |
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

### 8) 인증/승인: 공개 체험과 운영 보호 분리

검토한 안:
  - 데모 계정만 제공하고 일반 가입을 막음
  - access token만 사용하는 단순 JWT 인증
  - access token + refresh cookie 회전 + 관리자 승인 상태 모델

최종 선택:
  - 일반 사용자는 회원가입 후 `PENDING` 상태로 시작하고, 관리자가 `APPROVED/REJECTED`로 전환
  - API 호출은 bearer access token을 사용하고, 재발급은 HttpOnly refresh cookie와 CSRF cookie/header로 보호
  - 관리자 API는 `ADMIN` 역할만 접근 가능하도록 분리

선택 이유:
  - 포트폴리오 방문자는 데모 계정으로 빠르게 체험하고, 실제 가입자는 승인 흐름으로 운영 리스크를 낮출 수 있음

포기한 점(트레이드오프):
  - 클라이언트 인증 상태, 토큰 회전, CSRF 처리 코드가 늘어나지만 공개 서비스 운영 안전성을 우선

### 9) 이메일 알림: 발송 자체보다 운영 가능한 흐름

검토한 안:
  - 화면에서만 DUE_NOW를 노출
  - 단일 수신자에게 즉시 메일만 발송
  - 사용자별 설정, 수신자 목록, 발송 스케줄, 발송 로그를 함께 관리

최종 선택:
  - 사용자별 `enabled/sendTime/timezone` 설정, 내부 `nextSendAt` 스케줄, 수신자 목록을 관리
  - 스케줄러가 발송 대상 설정을 잠금 기반으로 가져와 중복 발송을 방어
  - 로컬 프로필에서는 `/api/alerts/task-due-email/test-send`로 템플릿과 발송 흐름을 즉시 검증

선택 이유:
  - 알림은 중복/실패/수신자 변경을 다뤄야 신뢰할 수 있으므로, 처음부터 설정과 로그 중심으로 설계

포기한 점(트레이드오프):
  - 메일 설정과 스케줄러 운영 부담이 생기지만, 사용자가 앱에 접속하지 않아도 행동을 유도할 수 있음

### 10) 인사이트 계산: projection 기반 요청 시 집계

검토한 안:
  - 매 요청마다 엔티티 그래프를 순회해 집계
  - 별도 요약 테이블/배치로 지표를 미리 저장
  - 완료 이력 projection과 활성 Task 목록을 조합해 요청 시 집계

최종 선택:
  - `task_completions`는 projection으로 조회하고, 활성 Task 기준으로 완료율/지연률/위험 작업을 계산
  - `topCompletionTrends`, `topDelayedTrends`, `topDelayRateTrends`를 분리해 서로 다른 해석 기준을 제공

선택 이유:
  - 현재 규모에서는 별도 집계 테이블 없이도 정합성을 유지할 수 있고, lazy loading/N+1 리스크를 줄일 수 있음

포기한 점(트레이드오프):
  - 데이터가 크게 늘어나면 인덱스/캐시/요약 테이블을 추가해야 하므로, 부하 테스트와 쿼리 관측을 계속 봐야 함

## 결과

### 사용자 관점

- “오늘 할 일” 중심으로 우선순위 판단 시간이 줄어듦
- 완료 이력을 월/날짜 단위로 확인해 작업 리듬을 해석 가능
- 인사이트 화면에서 오늘 완료한 일, 오래 밀린 일, 30일 동안 손대지 않은 일을 따로 확인해 정리/삭제/재조정 판단 가능
- 이메일 알림으로 앱에 접속하지 않아도 오늘 처리해야 할 작업을 받을 수 있음
- 회원가입 후 승인 대기/거절/승인 상태가 분리되어 공개 서비스에서도 사용 흐름이 명확함
- 완료 시 피드백과 중복 처리 방어로 상호작용 안정성 향상
- 모달/버튼/에러/rate-limit 흐름 개선으로 키보드 사용자와 실패 상황 사용자 경험 품질 향상

### 기술/운영 관점

- 상태 계산 일원화로 모델 정합성 강화
- 완료 처리 동시성 방어 + 이력 저장으로 데이터 신뢰성 강화
- owner 기반 사용자 범위 분리와 관리자 승인 API로 공개 가입 운영 리스크 축소
- refresh token 회전, CSRF 보호, rate-limit, 계정 잠금 컬럼으로 인증 계층 방어선 확보
- 이메일 알림 설정/수신자/발송 로그를 분리해 스케줄러 운영과 실패 추적 가능
- requestId + metrics + health 구성으로 “동작함”에서 “운영 가능함”으로 확장
- 이벤트 기반 로깅 분리로 서비스 코드 응집도 유지 및 운영 정책 확장성 확보
- 라우트 기반 데이터 로딩과 projection 조회로 불필요 API 호출 및 lazy loading 경합 리스크 감소

### 확인 포인트

- 회원가입 후 `PENDING` 사용자는 서비스 화면 접근이 제한되고, 관리자 승인 후 접근 가능한지
- 로그인/refresh/logout에서 access token, refresh cookie, CSRF 처리가 의도대로 동작하는지
- Task 완료 후 `next_due_at`이 완료 시각 기준으로 갱신되는지
- 상세 화면 월별/날짜별 이력이 저장 데이터와 일치하는지
- 인사이트 화면에서 오늘 완료, 위험 작업, 7일 이상 지연, 30일 무완료 신호가 API 응답과 일치하는지
- 알림 설정/수신자 변경 후 설정 응답, 수신자 목록, 최근 발송 결과가 갱신되는지
- 같은 요청의 requestId가 응답/로그/에러에 연결되는지
- `/actuator/metrics/http.server.requests`에서 URI별 요청량/지연시간이 관측되는지
- 모달 열기/닫기 시 포커스 이동·복귀, Esc 닫기, Tab 순환이 정상 동작하는지
- 실패 시 전역 에러와 로컬 폼 에러가 과도하게 중복되지 않는지, 재시도 동선이 제공되는지

### 한계와 다음 단계

- 인사이트 집계는 현재 요청 시 계산 방식입니다. 데이터가 커지면 쿼리 인덱스, 캐시, 요약 테이블 전환을 검토해야 합니다.
- Grafana 대시보드는 1차 구성 완료, `API down`/`5xx`/`p95` 임계치 기반 알림 자동화는 다음 단계입니다.
- fixed-token 조건의 API/DB 부하는 안정화했지만, 로그인/refresh/token 만료/rate-limit까지 포함한 인증 계층 부하 검증은 별도 과제입니다.
- 이메일 알림은 MVP 흐름이 구현되어 있으며, 운영 알림/재시도 정책/전달률 지표 고도화 여지가 있습니다.
- 실패 UX는 주요 흐름에 반영했지만, 더 세분화된 에러 코드별 안내와 복구 동선은 계속 개선할 수 있습니다.

## 주요 엔드포인트 요약

Base URL: `/api` (`/healthz`, `/actuator/**` 제외)

- 공개/인증 예외: `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`
- 그 외 API는 bearer access token 필요
- `/api/admin/**`는 `ADMIN` 역할 필요
- `refresh/logout`은 refresh cookie와 CSRF cookie/header 흐름을 사용

### 인증/사용자

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/auth/signup` | 회원가입. 신규 사용자는 승인 대기(`PENDING`) 상태로 생성 |
| `POST` | `/auth/login` | 로그인. access token 반환, refresh/CSRF cookie 설정 |
| `POST` | `/auth/refresh` | refresh token 회전 후 access token 재발급 |
| `POST` | `/auth/logout` | refresh token 폐기 및 인증 cookie 제거 |
| `GET` | `/auth/me` | 현재 로그인 사용자 정보 조회 |

### Task

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

### 인사이트

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/insights/dashboard` | 전체/기한초과/오늘/예정/오늘 완료/최근 7일 완료 요약 |
| `GET` | `/insights/overview?days=30&top=5` | 완료율, 지연률, 평균 지연일, 위험 작업, 완료/지연 Top 추세 조회 |
| `GET` | `/insights/recent-completions` | 최근 완료 작업 5건 조회 |
| `GET` | `/insights/today-completions` | KST 오늘 완료한 작업 조회 |

### 이메일 알림

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/alerts/task-due-email/settings` | 작업 마감 이메일 알림 설정과 최근 발송 결과 조회 |
| `PATCH` | `/alerts/task-due-email/settings` | 알림 활성화, 발송 시각, timezone 설정 수정 |
| `GET` | `/alerts/task-due-email/recipients` | 알림 수신자 목록 조회 |
| `POST` | `/alerts/task-due-email/recipients` | 알림 수신자 추가 |
| `DELETE` | `/alerts/task-due-email/recipients/{id}` | 알림 수신자 삭제 |
| `POST` | `/alerts/task-due-email/test-send` | 로컬 프로필 전용 즉시 발송 테스트 |

### 관리자

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/admin/users/pending` | 승인 대기 사용자 목록 조회 |
| `GET` | `/admin/users/non-pending` | 승인/거절 사용자 목록 조회 |
| `POST` | `/admin/users/{userId}/approve` | 승인 대기 사용자 승인 |
| `POST` | `/admin/users/{userId}/reject` | 승인 대기 사용자 거절 |
| `PATCH` | `/admin/users/{userId}/status` | 승인/거절 사용자 상태 전환 |

## 인사이트 지표 정의

### `/api/insights/dashboard`

- `totalTasks`: 현재 사용자의 활성 Task 수
- `overdueTasks`: KST 오늘 시작보다 `next_due_at`이 이전인 활성 Task 수
- `todayTasks`: KST 오늘 범위에 `next_due_at`이 포함된 활성 Task 수
- `upcomingTasks`: KST 내일 시작 이후 예정된 활성 Task 수
- `completedToday`: KST 오늘 완료한 이력 수
- `completedLast7Days`: 현재 시각 기준 최근 7일 완료 이력 수

### `/api/insights/overview?days=30&top=5`

- 파라미터 제한: `days`는 `1~365`, `top`은 `1~20`
- 기간: `periodStart <= completed_at < periodEnd` (`periodStart = now - days`, `periodEnd = now`, UTC 응답 + `timezone=Asia/Seoul`)
- `activeTaskCount`: 현재 활성 Task 수
- `completedTaskCount`: 기간 내 1회 이상 완료한 활성 Task 수
- `completionCount`: 기간 내 활성 Task 완료 이력 수
- `delayedCompletionCount`: `completed_at > previous_due_at`인 완료 이력 수
- 완료율(`completionRatePct`): `completedTaskCount / activeTaskCount * 100` (소수점 1자리)
- 지연률(`delayRatePct`): `delayedCompletionCount / completionCount * 100` (소수점 1자리)
- 평균 지연일(`averageDelayDays`): 지연 완료 건의 `completed_at - previous_due_at` 평균 일수 (소수점 2자리)
- 위험 작업(`riskyTasks`): 활성 Task 중 아래 신호가 있는 작업 목록
  - `OVERDUE_7D_PLUS`: `next_due_at`이 현재 시각보다 7일 이상 지남
  - `NO_COMPLETION_30D`: 완료 이력이 없거나 마지막 완료가 현재 시각보다 30일 이상 이전
- `riskyTaskCount`: `riskyTasks` 개수
- `topCompletionTrends`: 완료 건수 내림차순 Top N
- `topDelayedTrends`: 지연 완료 건수 내림차순 Top N
- `topDelayRateTrends`: 지연률 내림차순 Top N
- `taskTrends`: 하위 호환용 필드. 현재는 `topCompletionTrends`와 동일

## 관측성 엔드포인트

- Lightweight Health: `/healthz`
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Prometheus: `/actuator/prometheus`

`http.server.requests`로 요청량(count), 오류율(status), 응답시간(latency), endpoint별 병목을 추적할 수 있습니다. API 응답/로그에는 `X-Request-Id`가 연결되어 Grafana 수치에서 실제 requestId와 예외 로그까지 이어서 확인할 수 있습니다.

## Grafana 운영 가이드

Grafana/Prometheus 운영 방법, 대시보드 확인 루틴, 문제 해결은 [infra/README.md](infra/README.md)를 기준으로 확인합니다.

## 부하 테스트 결과 문서

부하테스트는 최대 RPS만 측정하는 방식이 아니라, read 기준선 확보 -> read/write 혼합 부하 -> 장시간 soak -> 인증 변수 분리 -> 500 원인 수정 -> 재검증 순서로 진행했습니다.

- 종합 정리: [docs/load-testing-summary.md](docs/load-testing-summary.md)

### 전체 흐름 요약

| 단계 | 목적 | 핵심 결과 |
|------|------|----------|
| Read Matrix | read API 용량 기준선 확인 | `80 VU`, `553.22 RPS`, p95 `8.11ms`, 실패율 `0%` |
| Mixed Peak | read/write 혼합 시 병목 확인 | peak 약 `600 RPS`까지 처리했지만 `401/429` 대량 발생, `recent-completions` 500 발견 |
| 초기 Soak | 장시간 부하에서 실패 누적 확인 | `60 VU`, `2h`; latency는 낮았지만 `401/429` 장기 누적 확인 |
| Fixed Token Mixed | 인증 변수를 제거하고 API 본체 확인 | 실패율이 `45.42%` -> `0.0243%`로 감소, 남은 실패가 `recent-completions` 500으로 좁혀짐 |
| Projection 수정 후 Mixed 재검증 | 500 수정 효과 확인 | `972,932` requests, 평균 `463.20 RPS`, 실패율 `0%`, checks `100%` |
| Fixed Token Soak | 수정 후 장시간 안정성 확인 | `60 VU`, `2h`, `3,507,776` requests, 평균 `487.14 RPS`, 실패율 `0%`, 5xx/500/401/429 `0건` |

### 부하테스트로 얻은 것

1. Read API 기준선 확보
   - read-only API는 `80 VU`까지 실패 없이 처리했습니다.
   - 따라서 현재 홈서버 로컬 환경에서 read API 자체는 목표 수준의 동시접속 부하를 안정적으로 처리한다고 판단했습니다.

2. 인증 문제와 API 본체 문제 분리
   - 초기 mixed/soak에서 `401/429`가 대량 발생했습니다.
   - 같은 mixed 부하를 고정 access token으로 재실행하자 실패율이 급감해, 대량 실패의 중심이 API 처리량 부족이 아니라 인증 재로그인/rate-limit 경합임을 확인했습니다.

3. `recent-completions` 500 원인 확정 및 수정
   - Grafana의 5xx endpoint와 requestId 기반 로그를 연결해 `EntityNotFoundException`을 확인했습니다.
   - 원인은 `TaskCompletion -> Task` lazy loading 중 연결된 Task가 삭제되는 read/write 경합이었습니다.
   - `recent-completions`, `today-completions`를 DTO projection 조회로 변경해 lazy loading 경합을 제거했습니다.

4. `overview` projection 리팩터링
   - 새 인사이트 API인 `/api/insights/overview`도 유사한 lazy/N+1 리스크가 있는지 점검했습니다.
   - 즉시 500 위험은 낮았지만, 장시간 부하와 데이터 증가를 고려해 projection 기반 조회로 선제 리팩터링했습니다.

5. 관측성 강화
   - 부하테스트 종료 직후 API/DB 로그, 5xx/500/401/429 access log, requestId trace, exception summary를 결과 폴더에 자동 저장하도록 개선했습니다.
   - 이후 500이 다시 발생하면 Grafana 수치에서 끝나지 않고 requestId와 stack trace까지 빠르게 연결할 수 있습니다.

### 현재 판단

현재까지 검증한 범위에서는, 인증 변수를 제거한 조건에서 API 본체와 DB 접근 패턴은 `50 VU` mixed peak 및 `60 VU` 2시간 soak 부하를 안정적으로 처리했습니다. 특히 projection 수정 이후 fixed-token mixed와 fixed-token soak 모두 실패율 `0%`, checks `100%`를 기록했습니다.

다만 이 결론은 로그인/refresh/token 만료/rate-limit까지 포함한 인증 계층 전체의 안정성을 의미하지는 않습니다. 초기 테스트에서 `401/429`가 실제로 크게 드러났기 때문에, 인증 안정화는 별도 시나리오로 검증해야 합니다.

### 상세 결과 문서

- Read Matrix 결과: [infra/load/results/local-read-matrix-20260512-102647/README.md](infra/load/results/local-read-matrix-20260512-102647/README.md)
- Mixed Peak 결과: [infra/load/results/local-mixed-peak-20260517-043551/README.md](infra/load/results/local-mixed-peak-20260517-043551/README.md)
- 초기 Soak 결과: [infra/load/results/local-soak-20260517-055109/README.md](infra/load/results/local-soak-20260517-055109/README.md)
- Fixed Token Mixed 결과: [infra/load/results/local-mixed-fixed-token-20260530-121407/README.md](infra/load/results/local-mixed-fixed-token-20260530-121407/README.md)
- 500 원인 분석: [infra/load/results/local-mixed-fixed-token-20260530-121407/500-root-cause-analysis.md](infra/load/results/local-mixed-fixed-token-20260530-121407/500-root-cause-analysis.md)
- Fixed Token Mixed 재검증: [infra/load/results/local-mixed-fixed-token-20260531-005152/README.md](infra/load/results/local-mixed-fixed-token-20260531-005152/README.md)
- Fixed Token Soak 결과: [infra/load/results/local-soak-fixed-token-20260531-022322/README.md](infra/load/results/local-soak-fixed-token-20260531-022322/README.md)

## 확장 계획

| 항목 | 상태 | 메모 |
|------|------|------|
| 완료 시점 기반 반복 Task 모델 | 완료 | `next_due_at = completed_at + every_n_days` 기준 모델 적용 |
| 멀티유저/관리자 승인 | 완료 | 사용자별 Task 범위 분리, `PENDING/APPROVED/REJECTED`, `USER/ADMIN` 적용 |
| 공개 데모/운영 도메인 분리 | 완료 | Cloudflare Tunnel 기반 공개 체험과 관리자/운영 도구 접근 분리 |
| 이메일 알림 MVP | 완료 | 사용자별 설정, 수신자 관리, 스케줄러 발송, 로컬 테스트 발송 적용 |
| 인사이트 재구성 | 완료 | 오늘 완료, 위험 작업, 7일 이상 지연, 30일 무완료, 완료/지연 Top 추세 제공 |
| Grafana/Prometheus 대시보드 | 완료 | 요청량, 오류율, p95, endpoint Top5 확인 가능 |
| 부하 테스트와 500 원인 수정 | 완료 | fixed-token mixed/soak 재검증에서 실패율 0%, checks 100% 확인 |
| 인증 계층 부하 검증 | 다음 | 로그인/refresh/token 만료/rate-limit 포함 시나리오 재검증 필요 |
| Grafana Alerting 고도화 | 다음 | `API down`, `5xx`, `p95` 임계치 알림과 Slack/Discord/Email 연동 |
| 인사이트 집계 최적화 | 다음 | 데이터 증가 시 인덱스, 캐시, 요약 테이블 전환 검토 |
| 이메일 알림 운영 지표 | 다음 | 전달률, 실패율, 재시도 정책, 발송 장애 알림 고도화 |
