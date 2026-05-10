# Work Units (2026-05-10)

- `p1-cloudflare-tunnel-docker-profile.md`
  - Docker Compose에 `cloudflared` 프로파일을 붙여 터널을 선택적으로 실행하도록 구성.
- `p1-localhost-port-binding-hardening.md`
  - 원본 서버 포트를 `127.0.0.1` 바인딩으로 고정해 직접 노출을 차단.
- `p1-cloudflare-edge-public-admin-split.md`
  - 공개/관리 트래픽을 Cloudflare 엣지 정책으로 분리하고 관리자 경로를 추가 보호.
- `p1-demo-account-login-entry.md`
  - 로그인 화면에 데모 계정 접기/펼치기 안내를 추가.
- `p0-demo-account-daily-reset.md`
  - 데모 계정 데이터를 매일 자동 리셋(세션 만료 + Task 초기화 + 샘플 재생성)하도록 구현.
- `p2-readme-env-source-of-truth.md`
  - 운영/env 문서를 `infra/README.md` 중심으로 정리해 중복 제거.
