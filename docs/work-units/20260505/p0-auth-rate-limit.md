# Work Unit: p0-auth-rate-limit

## Problem
- 현재 인증 엔드포인트(`login/signup/refresh`)에 요청 빈도 제한이 없어 과호출/브루트포스 시도에 취약한 상태.
- 계정 잠금 정책은 존재하지만, 엔드포인트 레벨에서 트래픽 자체를 줄이는 1차 방어선이 부족함.
- 클라이언트가 재시도할 때 대기 기준이 없어(`Retry-After` 부재) 운영/UX 일관성이 떨어짐.

## Decision
- 선택한 방향: 인메모리 fixed-window 기반 rate-limit 우선 적용
- 세부 구현 방향: 필터에서 `IP` 1차 제한, 서비스 레이어에서 `IP+email` 2차 제한 적용
- 결정 이유: 필터 바디 파싱 복잡도를 피하면서도 공용 IP 과호출과 계정 타깃 공격을 함께 방어할 수 있음.

## Trade-offs
- 장점: 구현 리스크를 낮추면서 빠르게 방어선을 추가할 수 있고, 429/Retry-After 응답 기준을 조기 확립 가능.
- 단점/리스크: 다중 인스턴스 환경에서는 인스턴스별 카운터로 동작하며, 재시작 시 카운터가 초기화됨.

## Implementation Summary
- 변경 파일:
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/security/AuthRateLimitFilter.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/security/AuthRateLimitGuard.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/security/AuthRateLimitConfig.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/security/InMemoryFixedWindowRateLimiter.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/security/RateLimitResult.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/security/RateLimitViolation.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/security/SecurityConfig.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/service/AuthService.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/exception/AuthException.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/common/exception/GlobalExceptionHandler.java`
  - `apps/api/src/main/resources/application-local.yml`
- 핵심 로직:
  - `login/signup/refresh` 요청에 서버 측 rate-limit 적용
  - 필터에서 `IP` 기준 1차 제한 적용
  - 서비스 레이어에서 `login/signup`에 `IP+email` 기준 2차 제한 적용
  - 제한 초과 시 `429`와 `Retry-After` 헤더 반환

## How To Test
- 화면 테스트 절차:
  - 로그인 화면에서 같은 이메일/비밀번호로 연속 로그인 요청을 빠르게 반복
  - 브라우저 개발자도구 Network에서 `POST /api/auth/login` 상태코드/헤더 확인
  - 이미 로그인된 상태에서 새로고침을 빠르게 반복해 `POST /api/auth/refresh` 응답 확인
- 기대 결과:
  - 임계치 전에는 정상 응답(`200/401/403`) 유지
  - 임계치 초과 시 `429` 응답과 `Retry-After` 헤더 노출
  - `429` 응답 바디의 `error.code`가 rate-limit 코드로 반환

## Related Tests
- `apps/api/src/test/java/com/yegkim/task_reloader_api/auth/controller/AuthControllerTest.java`
  - 로그인 rate-limit 초과 시 `429`와 `Retry-After` 헤더 반환 검증
- `apps/api/src/test/java/com/yegkim/task_reloader_api/auth/security/AuthRateLimitFilterTest.java`
  - 대상 경로/메서드 판별, 허용/차단 흐름, 차단 시 `Retry-After` 헤더 설정 검증
- `apps/api/src/test/java/com/yegkim/task_reloader_api/auth/security/AuthRateLimitGuardTest.java`
  - IP 제한 violation 반환, IP+email 제한 초과 시 `AuthException(429)` 검증
- `apps/api/src/test/java/com/yegkim/task_reloader_api/auth/service/AuthServiceAuthFlowTest.java`
  - signup/login에서 rate-limit 초과 시 저장/조회 로직 진입 전 차단 검증
