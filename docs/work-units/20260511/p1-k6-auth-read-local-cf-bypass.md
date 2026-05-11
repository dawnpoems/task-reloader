# Work Unit: p1-k6-auth-read-local-cf-bypass

## Problem
- 현재 인증 구조에서는 Task/Insights API가 로그인 필요라 기존 read-only k6 스크립트가 401로 실패할 수 있음.
- auth rate-limit 정책 때문에 VU마다 로그인 호출을 반복하면 부하 테스트 자체가 로그인 제한에 막혀 왜곡될 수 있음.
- Cloudflare 영향 없이 홈서버 자체 성능을 보려면 localhost 대상으로 부하를 보내야 함.

## Decision
- Cloudflare 우회(홈서버 localhost 대상) 전용 인증 포함 읽기 부하 스크립트를 추가.
- 기본 전략은 `setup()`에서 로그인 1회 후 access token 공유.
- 필요 시 사전 발급 토큰(`ACCESS_TOKEN`)으로 로그인 호출 없이 실행 가능하게 구성.

## Trade-offs
- 장점: 인증이 필요한 운영 구성에서도 rate-limit 영향 최소화로 읽기 API 성능을 안정적으로 측정 가능.
- 단점/리스크: 단일 토큰 공유 방식이라 “다중 사용자 동시 로그인” 시나리오는 직접 반영하지 않음.

## Implementation Summary
- 변경 파일:
  - `infra/load/k6-auth-read-local.js`
  - `infra/README.md`
- 핵심 반영:
  - 인증 토큰 부트스트랩(`ACCESS_TOKEN` 또는 `AUTH_EMAIL`/`AUTH_PASSWORD`) 지원.
  - `/api/tasks`, `/api/insights`, `/api/tasks/{id}`, `/api/tasks/{id}/completions` 배치 호출.
  - `BASE_URL=http://127.0.0.1:3000|8080` 기준 Cloudflare 우회 실행 가이드 추가.
  - `k6 inspect`로 스크립트 파싱 검증 완료.

## How To Test
- 홈서버에서 `BASE_URL=http://127.0.0.1:3000`로 실행해 Cloudflare 로그 증가 없이 테스트가 수행되는지 확인한다.
- 인증 계정 방식(`AUTH_EMAIL`/`AUTH_PASSWORD`)과 토큰 방식(`ACCESS_TOKEN`) 모두 실행 확인한다.
- 응답 지표(`http_req_failed`, endpoint별 p95, checks)가 threshold를 만족하는지 확인한다.

## Related Tests
- 자동 테스트 코드: 없음 (k6 실행 기반 검증)
