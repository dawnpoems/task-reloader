# Work Unit: p1-single-origin-auth-security-config

## Problem
- 운영 환경에서 CSRF 허용 오리진, 쿠키 보안 플래그, rate-limit 전달 경로가 문서/설정에 분산되어 있어 배포 시 오설정 위험이 있음.
- `AUTH_CSRF_ALLOWED_ORIGINS`가 실제 Web Origin과 다르면 refresh/logout이 정상 사용자에게도 `403`으로 실패할 수 있음.
- compose 환경변수 전달 목록에 CSRF/rate-limit 항목이 일부 누락되어 운영값 제어 일관성이 떨어짐.

## Decision
- 단일 오리진 운영을 기준으로 보안 설정을 확정.
- Web Origin 1개를 `AUTH_CSRF_ALLOWED_ORIGINS`에 명시하고, 쿠키 `Secure=true`, `SameSite=Lax`를 운영 기본값으로 사용.
- compose API 환경변수에 CSRF/rate-limit 설정을 명시적으로 전달.

## Trade-offs
- 장점: 설정 기준이 단순해지고, 인증/세션 재발급 실패 원인 추적이 쉬워짐.
- 단점/리스크: 향후 다중 오리진 배포 시 CORS/쿠키 정책 재설계가 추가로 필요함.

## Implementation Summary
- 변경 파일:
  - `infra/.env.example`
  - `infra/docker-compose.yml`
  - `README.md`
  - `infra/README.md`
- 핵심 반영:
  - 단일 오리진 기준 CSRF/Cookie 운영 권장값 추가
  - compose에서 CSRF/rate-limit 관련 env를 API 컨테이너로 전달
  - 운영 보안 체크리스트 문서화

## How To Test
- 화면 테스트 절차:
  - Web에 로그인 후, 새로고침/로그아웃을 반복해 세션 갱신/종료 흐름을 확인
  - 브라우저 Network에서 `/api/auth/refresh`, `/api/auth/logout` 응답 상태를 확인
  - 과도한 로그인/리프레시 요청 시 `429`와 `Retry-After` 헤더가 내려오는지 확인
- 기대 결과:
  - 허용된 Origin에서는 refresh/logout이 정상 동작
  - 허용되지 않은 Origin에서는 `403`으로 차단
  - 과호출 시 rate-limit 정책대로 `429` 응답

## Related Tests
- 자동 테스트 코드: 기존 rate-limit/CSRF 테스트 스위트로 회귀 확인 가능
