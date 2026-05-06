# Work Unit: p2-auth-429-ux

## Problem
- 로그인/회원가입에서 429(rate-limit) 발생 시 사용자가 얼마나 기다려야 하는지 즉시 알기 어렵고, 연속 클릭으로 동일 실패를 반복하기 쉬움.
- 현재는 에러 문구만 노출되어 재시도 타이밍 가이드(`Retry-After`)가 UX에 반영되지 않음.

## Decision
- 인증 화면(Login/Signup) 범위에서만 429 UX를 우선 개선.
- `Retry-After`를 읽어 남은 대기시간을 안내하고, 대기 중 제출 버튼을 잠금.

## Trade-offs
- 장점: 영향 범위를 제한하면서 체감 UX를 빠르게 개선 가능.
- 단점/리스크: 인증 외 화면의 429 처리 경험은 기존 상태로 남음.

## Implementation Summary
- 변경 파일:
  - `apps/web/src/api/client.ts`
  - `apps/web/src/auth/AuthContext.tsx`
  - `apps/web/src/hooks/useRetryAfterCountdown.ts`
  - `apps/web/src/components/AuthLoginPage.tsx`
  - `apps/web/src/components/AuthSignupPage.tsx`
  - `apps/web/src/App.css`
- 핵심 반영:
  - API 에러 객체에 `retryAfterSeconds`를 전달하도록 확장.
  - 로그인/회원가입에서 `retryAfterSeconds` 수신 시 실시간 카운트다운 시작.
  - 카운트다운 동안 제출 버튼 잠금 + 남은 초 UI 노출.
  - 제출 버튼 비활성 상태를 회색 톤으로 명확하게 표시해 상태 인지성 강화.
  - 카운트다운 로직을 공통 훅(`useRetryAfterCountdown`)으로 분리.

## How To Test
- 로그인 화면:
  - 짧은 시간에 로그인 요청을 반복해 429를 유도한다.
  - 에러 응답 후 경고 박스에 남은 초가 1초 단위로 줄어드는지 확인한다.
  - 카운트다운 중 로그인 버튼이 비활성화되고, 텍스트가 `N초 후 재시도`로 바뀌는지 확인한다.
  - 카운트다운이 0이 되면 버튼이 다시 활성화되는지 확인한다.
- 회원가입 화면:
  - 짧은 시간에 가입 요청을 반복해 429를 유도한다.
  - 로그인 화면과 동일하게 카운트다운/버튼 잠금/재활성화가 동작하는지 확인한다.

## Related Tests
- 자동 테스트 코드: (다음 단계에서 추가 예정)
