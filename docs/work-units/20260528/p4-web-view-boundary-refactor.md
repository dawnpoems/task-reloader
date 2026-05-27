# 웹 화면 경계 추가 정리

## 문제

- 이전 `동작 보존 중심 구조 리팩토링` 이후에도 `App.tsx`에 Task 생성/수정/삭제/완료 후 refresh orchestration이 남아 있었다.
- 알림 설정 페이지는 데이터 로직은 hook으로 빠졌지만 조회 패널과 편집 모달 JSX가 한 파일에 남아 있었다.
- 관리자 승인 페이지도 데이터/액션 로직은 hook으로 빠졌지만 승인 대기 목록, 승인/거절 사용자 목록, 확인 모달 JSX가 한 파일에 남아 있었다.
- 공통 async helper화 후보는 있었지만, 각 hook의 에러 문구와 동작 차이가 커서 지금 일반화하면 추상화 비용이 더 커질 수 있었다.

## 결정

- API 응답 JSON shape를 런타임에서 endpoint별로 검증하는 작업은 이번 범위에서 제외했다.
- `App.tsx`의 Task mutation/refetch 흐름은 `useTaskWorkflow`로 분리했다.
- 알림 설정 화면은 조회 패널과 편집 모달을 별도 view 컴포넌트로 분리했다.
- 관리자 승인 화면은 사용자 목록/확인 모달을 별도 view 컴포넌트로 분리했다.
- `useTasks`, `useInsights`, `useDashboardSummary`의 generic async helper화는 진행하지 않았다.

## 트레이드오프

- view 컴포넌트 파일 수는 늘었지만, page 컴포넌트의 책임과 라인 수가 줄었다.
- JSX 이동이 많아 import/props 연결 실수 가능성이 있었으나 type-check, lint, build로 확인했다.
- generic async helper를 보류해 일부 loading/error 패턴 중복은 남지만, 각 도메인 hook의 에러 처리 의도를 유지했다.

## 작업 내용

- `useTaskWorkflow`를 추가해 Task 생성/수정/삭제/완료 후 refresh, 완료 애니메이션 상태, 상세 refresh token을 관리하도록 했다.
- `App.tsx`에서 Task workflow 상태와 handler를 제거하고, 화면 조합과 인증/라우팅 흐름에 집중하도록 정리했다.
- `AlertSettingsOverview`를 추가해 알림 설정 조회 패널과 읽기 전용 수신자 목록 렌더링을 분리했다.
- `AlertSettingsEditModal`을 추가해 발송 설정 폼, 수신 이메일 추가/삭제 폼, 모달 focus trap을 분리했다.
- 알림 설정 변경 모달에 `useModalFocusTrap`을 적용해 `Tab`, `Shift+Tab`, `Escape`, 포커스 복구 흐름을 공통화했다.
- `AdminApprovalsConfirmModal`을 추가해 승인/거절/상태 변경 확인 모달 렌더링과 focus trap을 분리했다.
- `AdminApprovalsUserLists`를 추가해 승인 대기 목록과 승인/거절 사용자 목록 렌더링을 분리했다.
- `useAdminApprovals`의 `ActionState` 타입을 view 컴포넌트에서 사용할 수 있도록 export했다.
- `AlertSettingsPage`와 `AdminApprovalsPage`는 hook 결과를 view 컴포넌트에 전달하는 얇은 page 컴포넌트 역할로 정리했다.

## 테스트 방법

- `/` 홈 화면에서 Task 생성, 수정, 삭제, 완료 후 오늘 할 일 목록과 남은 일정 카운트가 갱신되는지 확인한다.
- Task 완료 시 카드의 완료 처리 애니메이션이 기존처럼 잠시 표시되는지 확인한다.
- `/tasks/{id}` 상세 화면에서 완료 처리 후 상세 정보와 완료 이력이 갱신되는지 확인한다.
- `/alerts`에서 현재 알림 설정 카드와 수신 이메일 목록이 기존처럼 표시되는지 확인한다.
- `/alerts`의 `변경` 버튼을 눌러 모달을 열고 발송 설정 저장, 수신 이메일 추가/삭제, 중복/최대 개수 안내가 기존처럼 동작하는지 확인한다.
- 알림 설정 변경 모달에서 `Tab`, `Shift+Tab`, `Escape`를 눌러 포커스 이동과 닫기 동작이 자연스러운지 확인한다.
- 관리자 계정으로 `/admin/approvals`에 진입해 승인 대기 목록, 이메일 검색, 승인/거절 확인 모달을 확인한다.
- 승인/거절 사용자 목록을 펼친 뒤 상태 토글 확인 모달과 상태 변경 후 목록 갱신을 확인한다.
- 관리자 확인 모달에서 `Tab`, `Shift+Tab`, `Escape`를 눌러 기존 focus trap 동작이 유지되는지 확인한다.

## 관련 테스트

- `npm run type-check`
- `npm run lint`
- `./node_modules/.bin/vite build --outDir /private/tmp/task-reloader-web-build --emptyOutDir`
