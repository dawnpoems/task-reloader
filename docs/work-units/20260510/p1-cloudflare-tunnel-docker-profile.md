# Work Unit: p1-cloudflare-tunnel-docker-profile

## Problem
- 외부 공개를 위해 Cloudflare Tunnel을 붙이려면 기존 compose와 별도 실행 스크립트/수동 실행이 필요해 운영 절차가 번거로웠다.
- 로컬 개발/점검에서는 터널이 불필요한데 항상 함께 띄우면 운영 복잡도와 장애 표면이 커진다.

## Decision
- `docker-compose`에 `cloudflared` 서비스를 추가하되 `profile: tunnel`로 분리했다.
- 기본 실행은 기존과 동일하게 유지하고, 외부 공개가 필요할 때만 터널 프로파일을 활성화한다.

## Trade-offs
- 장점: 기존 개발 흐름을 깨지 않고 외부 공개 경로를 바로 활성화할 수 있다.
- 단점/리스크: Tunnel Token 누락/오입력 시 터널만 실패하고 앱은 정상 동작해 원인 파악을 놓칠 수 있다.

## Implementation Summary
- 변경 파일:
  - `infra/docker-compose.yml`
  - `infra/.env.example`
  - `infra/README.md`
- 핵심 반영:
  - `cloudflared` 서비스 추가(`tunnel --no-autoupdate run`).
  - `CLOUDFLARE_TUNNEL_TOKEN` 환경변수 추가.
  - Tunnel 생성/연결 절차를 인프라 문서에 추가.

## How To Test
- 기본 compose 실행 시(`tunnel` 프로파일 미사용) 기존 web/api/db가 정상 동작하는지 확인한다.
- 터널 프로파일 활성화 후 `cloudflared` 로그에서 연결 성공 이벤트가 지속적으로 출력되는지 확인한다.
- Public Hostname이 `web` 컨테이너로 라우팅되어 공개 도메인에서 앱이 열리는지 확인한다.

## Related Tests
- 자동 테스트 코드: 없음 (인프라/운영 경로 수동 검증)
