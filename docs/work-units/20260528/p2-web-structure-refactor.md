# 웹 프론트 구조 리팩토링

## 문제

- `App.tsx`가 라우팅, 인증 리다이렉트, 홈 화면 렌더링, 남은 일정 로딩, Task mutation 후 refresh 흐름을 함께 담당하고 있었다.
- `AlertSettingsPage`와 `AdminApprovalsPage`가 데이터 조회, mutation, 폼 상태, 모달 상태, 렌더링을 한 파일에 함께 들고 있어 변경 범위가 커지기 쉬웠다.
- Task 상세/수정 화면에서 완료 이력 조회가 중복되고, 여러 모달에서 focus trap과 포커스 복구 로직이 반복되고 있었다.
- API query string을 문자열 보간으로 직접 만들어 optional query가 늘어날수록 중복과 인코딩 실수 가능성이 있었다.

## 결정

- A안인 동작 보존 중심 구조 리팩토링을 먼저 진행했다.
- `ApiResponse` discriminated union 전환처럼 전역 API 응답 계약을 바꾸는 작업은 이번 범위에서 제외했다.
- 화면 동작과 문구는 유지하고, 책임 분리와 중복 제거만 반영했다.

## 트레이드오프

- 이번 작업으로 큰 컴포넌트의 책임은 줄었지만, `ApiResponse<T>`의 `success && data` 조건 반복은 아직 남아 있다.
- 파일 수는 늘어났지만 각 파일의 역할이 작아져 후속 변경 단위가 명확해졌다.
- 전역 타입 계약을 건드리지 않아 타입 품질 개선 폭은 제한했지만, 리팩토링 회귀 위험을 낮췄다.

## 작업 내용

- 라우트 상수, 알려진 경로 판별, 로그인 후 리다이렉트 저장/복구 로직을 `lib/routes.ts`로 분리했다.
- 브라우저 history 기반 이동과 `popstate` 처리를 `useBrowserNavigation` hook으로 분리했다.
- 홈 화면 렌더링을 `HomePage` 컴포넌트로 분리했다.
- 남은 일정 목록의 lazy load/open/error 상태를 `useUpcomingTasks` hook으로 분리했다.
- Task 상세 조회를 `useTaskDetail` hook으로 분리했다.
- Task 완료 이력 조회를 `useTaskCompletions` hook으로 분리하고 상세 화면/수정 모달에서 함께 사용하게 했다.
- 생성 모달, 수정 모달, 관리자 확인 모달의 포커스 이동/Tab trap/Escape 처리/포커스 복구를 `useModalFocusTrap` hook으로 공통화했다.
- 알림 설정 페이지의 조회, 저장, 수신자 추가/삭제, dirty/중복/제한 상태 계산을 `useAlertSettings` hook으로 분리했다.
- 관리자 승인 페이지의 대기 사용자 조회, 승인/거절 사용자 lazy load, 검색 필터, 승인/거절/상태 변경 mutation을 `useAdminApprovals` hook으로 분리했다.
- `withQuery` helper를 추가해 Task/Insight API query string 조립을 공통화했다.
- `App.tsx`, `AdminApprovalsPage.tsx`, `AlertSettingsPage.tsx`, `TaskDetailPage.tsx`, `TaskEditModal.tsx`의 직접 상태 관리와 중복 코드를 줄였다.

## 테스트 방법

- 로그인 후 `/` 홈 화면으로 진입한다.
- `남은 일정 펼치기`를 눌러 남은 일정 목록이 기존처럼 처음 열 때 로딩되고, 다시 누르면 접히는지 확인한다.
- `+ 새 Task`를 눌러 생성 모달을 열고 이름/반복 주기/시작 날짜를 입력한 뒤 저장했을 때 목록과 남은 일정 카운트가 갱신되는지 확인한다.
- Task 카드의 `상세`로 `/tasks/{id}`에 진입하고, 완료 달력에서 이전/다음 월 이동과 날짜 선택 시 완료 이력이 표시되는지 확인한다.
- 상세 화면 또는 카드에서 `수정`을 눌러 수정 모달을 열고, 완료 이력 로딩/재시도 버튼/저장/삭제 흐름이 기존처럼 동작하는지 확인한다.
- `/insights`로 진입해 리스크 작업의 상세 이동, 변경, 삭제 버튼 흐름이 기존처럼 동작하는지 확인한다.
- `/alerts`로 진입해 `변경` 모달을 열고 발송 시간/타임존 변경, 수신 이메일 추가/중복 입력/삭제/최대 개수 제한 메시지를 확인한다.
- 관리자 계정으로 `/admin/approvals`에 진입해 이메일 검색, 승인/거절 확인 모달, 승인/거절 사용자 펼치기, 상태 토글 확인 모달이 기존처럼 동작하는지 확인한다.
- 생성/수정/관리자 확인 모달에서 `Tab`, `Shift+Tab`, `Escape`를 눌러 포커스 이동과 닫기 동작이 유지되는지 확인한다.
- API 실패 상황에서는 각 화면의 기존 에러 메시지와 `다시 시도` 버튼이 표시되는지 확인한다.

## 관련 테스트

- `npm run type-check`
- `npm run lint`
- `./node_modules/.bin/vite build --outDir /private/tmp/task-reloader-web-build --emptyOutDir`
