# Work Unit: p1-localhost-port-binding-hardening

## Problem
- 원본 서버의 DB/API/Web/모니터링 포트가 외부 인터페이스로 열리면 Cloudflare 경유 정책을 우회해 직접 접근될 수 있다.
- 포트폴리오 공개 환경에서 원본 서버 직접 노출은 스캔/봇 트래픽 표면을 넓힌다.

## Decision
- `docker-compose`의 포트 바인딩을 전부 `127.0.0.1`로 고정했다.
- 외부 진입은 Cloudflare Tunnel/리버스 프록시 경유로만 처리하도록 경계를 명확히 했다.

## Trade-offs
- 장점: 원본 서버 직접 인바운드 경로를 줄여 보안 기본선을 즉시 높일 수 있다.
- 단점/리스크: 같은 서버 외부에서 직접 포트 점검이 필요할 때는 별도 SSH 터널링 또는 프록시가 필요하다.

## Implementation Summary
- 변경 파일:
  - `infra/docker-compose.yml`
- 핵심 반영:
  - `5432`, `8080`, `3000`, `9090`, `3001` 포트 매핑을 모두 `127.0.0.1:<hostPort>:<containerPort>`로 변경.

## How To Test
- 서버 외부 네트워크에서 원본 포트 직접 접속이 실패하는지 확인한다.
- 서버 로컬에서는 기존과 동일하게 포트 접속이 가능한지 확인한다.
- Cloudflare 경유 도메인에서는 앱/모니터링 페이지 접근이 정상인지 확인한다.

## Related Tests
- 자동 테스트 코드: 없음 (인프라/네트워크 경로 수동 검증)
