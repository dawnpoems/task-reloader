# Work Unit: p1-xff-overwrite-nginx

## Problem
- rate-limit IP 판별이 `X-Forwarded-For`를 참조하는 구조에서, 프록시가 client 헤더를 이어붙이면 스푸핑 우회 여지가 생길 수 있음.

## Decision
- 프록시 레벨에서 `X-Forwarded-For`를 append 하지 않고 `$remote_addr`로 overwrite.

## Trade-offs
- 장점: 최소 변경으로 헤더 신뢰 경계를 즉시 고정 가능.
- 단점/리스크: API를 프록시 우회로 직접 노출하면 앱 레벨 추가 방어가 없음.

## Implementation Summary
- 변경 파일:
  - `apps/web/nginx.conf`
- 핵심 반영:
  - `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`
  - `proxy_set_header X-Forwarded-For $remote_addr;` 로 변경.

## How To Test
- 프록시를 통해 `/api/auth/login` 요청 시 백엔드에서 `X-Forwarded-For` 값이 클라이언트 주입값이 아니라 프록시 관측 IP로 고정되는지 확인한다.
- 의도적으로 `X-Forwarded-For: 1.2.3.4` 헤더를 넣어도 rate-limit 키 IP가 변하지 않는지 확인한다.

## Related Tests
- 자동 테스트 코드: 다음 단계에서 추가 예정
