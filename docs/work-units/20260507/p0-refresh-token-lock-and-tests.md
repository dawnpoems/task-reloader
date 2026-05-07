# Work Unit: p0-refresh-token-lock-and-tests

## Problem
- refresh 토큰 회전 로직에서 동일 토큰 동시 요청 시 경쟁 조건이 발생할 수 있어, 1회 사용 보장이 약해질 수 있음.

## Decision
- refresh 토큰 조회 시 비관적 락(`PESSIMISTIC_WRITE`)을 적용해 동일 토큰 처리를 직렬화.
- 테스트 단계에서는 서비스 테스트를 락 조회 메서드 기준으로 보강.

## Trade-offs
- 장점: 최소 코드 변경으로 refresh 토큰 회전 안정성을 높일 수 있음.
- 단점/리스크: 동시 요청이 몰리면 락 대기 시간이 증가할 수 있음.

## Implementation Summary
- 변경 파일:
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/repository/RefreshTokenRepository.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/auth/service/AuthService.java`
  - `apps/api/src/test/java/com/yegkim/task_reloader_api/auth/service/AuthServiceAuthFlowTest.java`
- 핵심 반영:
  - `findByTokenHashForUpdate` 추가(`@Lock(PESSIMISTIC_WRITE)`).
  - `AuthService.refresh`가 락 조회 메서드를 사용하도록 변경.
  - refresh 서비스 테스트 스텁을 락 조회 메서드로 변경하고 호출 검증 추가.

## How To Test
- `AuthServiceAuthFlowTest`의 refresh 관련 케이스가 통과하는지 확인한다.
- 전체 API 테스트 실행 시 기존 인증/시큐리티 테스트에 회귀가 없는지 확인한다.

## Related Tests
- `apps/api/src/test/java/com/yegkim/task_reloader_api/auth/service/AuthServiceAuthFlowTest.java`
