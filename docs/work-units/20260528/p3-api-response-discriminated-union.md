# API 응답 타입 분리

## 문제

- 기존 `ApiResponse<T>`는 `success`, `data`, `error`가 모두 optional에 가까운 형태라 성공/실패 분기 이후에도 타입이 충분히 좁혀지지 않았다.
- 호출부에서 `res.success && res.data`를 반복해야 했고, 실패 분기에서 `res.error`가 항상 존재한다는 사실도 타입으로 표현되지 않았다.
- 삭제/로그아웃처럼 본문이 없는 성공 응답과 데이터가 필요한 성공 응답이 같은 optional `data` 모델로 섞여 있었다.

## 결정

- 검토한 선택지는 `TypeScript 타입 중심의 응답 분기 개선`과 `응답 JSON shape 런타임 검증까지 포함하는 개선`이었다.
- 이번 작업에서는 `TypeScript 타입 중심의 응답 분기 개선`을 진행해 `ApiResponse<T>`를 `success` discriminated union으로 전환했다.
- `ApiResponse<void>`는 성공 시 `data`가 없어도 되는 타입으로 두고, 그 외 성공 응답은 `data: T`를 요구하도록 했다.
- 런타임 응답 shape 검증까지 강제하는 작업은 이번 범위에서 제외했다.

## 트레이드오프

- 성공/실패 분기 타입 안정성은 좋아졌지만, 서버가 성공 응답에 `data`를 누락하는 비정상 응답까지 런타임에서 엄격히 검증하지는 않는다.
- 호출부의 `success && data` 패턴은 줄었지만, refresh token처럼 빈 문자열 방어가 필요한 곳은 별도 값 검사를 유지했다.
- HTTP 실패인데 본문이 `success: true`로 오는 비정상 케이스는 클라이언트에서 실패로 보정해 잘못된 성공 분기를 막았다.

## 작업 내용

- `ApiErrorBody` 타입을 추가해 API 에러 payload를 명시했다.
- `ApiSuccess<T>`, `ApiFailure`, `ApiResponse<T>`를 `success` 기준 union으로 변경했다.
- `ApiResponse<void>` 성공 응답은 `data` 없이 표현되도록 조건부 타입을 적용했다.
- 204/205/빈 본문 성공 응답은 `ApiResponse<T>`에 맞게 처리하고, 실패 응답은 항상 `error`를 포함하도록 보정했다.
- `Retry-After` 헤더가 있는 실패 응답은 객체/문자열 에러 모두 `retryAfterSeconds`를 유지하도록 정리했다.
- `extractErrorMessage`, `extractErrorCode`, `extractRetryAfterSeconds`가 `ApiErrorBody | undefined`를 받도록 변경했다.
- `res.success && res.data` 또는 `!res.success || !res.data`에 의존하던 호출부를 `res.success` 분기 중심으로 정리했다.

## 테스트 방법

- 로그인 화면에서 정상 로그인과 실패 로그인을 각각 시도한다.
- 요청 제한이 걸리는 로그인/회원가입 실패 응답에서는 남은 초 countdown이 기존처럼 표시되는지 확인한다.
- 홈 화면에서 Task 목록 조회, 생성, 수정, 삭제, 완료가 기존처럼 동작하는지 확인한다.
- `/insights`에서 인사이트 요약과 오늘 완료 목록이 기존처럼 표시되는지 확인한다.
- `/alerts`에서 알림 설정 조회, 저장, 수신자 추가/삭제 실패 메시지가 기존처럼 표시되는지 확인한다.
- 관리자 계정으로 `/admin/approvals`에서 승인 대기/승인·거절 사용자 목록 조회와 승인/거절 실패 메시지가 기존처럼 표시되는지 확인한다.

## 관련 테스트

- `npm run type-check`
- `npm run lint`
- `./node_modules/.bin/vite build --outDir /private/tmp/task-reloader-web-build --emptyOutDir`
