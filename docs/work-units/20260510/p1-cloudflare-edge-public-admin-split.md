# Work Unit: p1-cloudflare-edge-public-admin-split

## Problem
- 전체 도메인 화이트리스트 방식(Access 전면 보호)은 포트폴리오 공개성은 낮추고 체험 진입 장벽을 높인다.
- 반대로 전체 공개만 하면 관리자 경로(`/admin`, `/api/admin`)까지 같은 수준으로 노출되어 운영 리스크가 커진다.

## Decision
- 공개 사용자 트래픽과 관리자/운영 트래픽을 Cloudflare 엣지에서 분리했다.
- 공개 도메인은 앱 로그인 기반으로 열고, 관리자/운영 도메인만 Access 정책으로 보호했다.

## Trade-offs
- 장점: 포트폴리오 공개성(접근성)과 운영 안전성(관리자 보호)을 함께 가져갈 수 있다.
- 단점/리스크: Cloudflare 대시보드 정책이 분산되므로 도메인/경로 정책 변경 시 누락 위험이 있다.

## Implementation Summary
- 저장소 파일 변경:
  - 없음 (Cloudflare 대시보드 설정 작업)
- Cloudflare 반영 항목:
  - `task.dawnpoem.kr`: 공개 진입점으로 운영.
  - `admin-task.dawnpoem.kr`: Cloudflare Access로 관리자만 허용.
  - `grafana.dawnpoem.kr`: Cloudflare Access로 운영자만 허용.
  - `task.dawnpoem.kr` 경로 차단: `/admin/*`, `/api/admin/*`.

## How To Test
- 비로그인/일반 사용자로 `task.dawnpoem.kr` 접속이 가능한지 확인한다.
- 같은 조건에서 `task.dawnpoem.kr/admin/...`와 `/api/admin/...` 호출이 차단되는지 확인한다.
- `admin-task.dawnpoem.kr`, `grafana.dawnpoem.kr`는 Access 인증 없이는 진입되지 않는지 확인한다.
- 관리자 계정 + Access 인증을 통과하면 관리자 기능이 정상 동작하는지 확인한다.

## Related Tests
- 자동 테스트 코드: 없음 (Cloudflare 엣지 정책 수동 검증)
