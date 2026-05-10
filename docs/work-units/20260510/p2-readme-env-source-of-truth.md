# Work Unit: p2-readme-env-source-of-truth

## Problem
- 메인 `README.md`와 `infra/README.md`에 운영/env 설정이 중복되어 수정 누락과 불일치 가능성이 있었다.
- 최근 `.env` 키가 늘어난 상태에서 두 문서를 동시에 유지하면 운영 반영 실수가 발생하기 쉽다.

## Decision
- 운영 실행, `.env`, Tunnel, 운영 체크리스트의 단일 기준 문서를 `infra/README.md`로 통합했다.
- 메인 README는 제품 소개/아키텍처 중심으로 유지하고 운영 상세는 참조 링크로만 안내한다.

## Trade-offs
- 장점: 운영 문서 업데이트 지점을 1곳으로 줄여 문서 신뢰도와 유지보수성이 높아진다.
- 단점/리스크: 메인 README만 보는 사용자는 운영 상세를 한 번 더 이동해서 확인해야 한다.

## Implementation Summary
- 변경 파일:
  - `README.md`
  - `infra/README.md`
- 핵심 반영:
  - 메인 README의 운영/env/Grafana 상세 본문 제거.
  - 메인 README에서 `infra/README.md`를 단일 기준 문서로 명시.
  - `infra/README.md` 상단에 단일 기준 문서임을 명확히 표기.
  - 최신 env 키(인증, rate-limit, 데모 리셋, Tunnel) 예시를 인프라 문서에 일관 반영.

## How To Test
- 메인 README에서 운영 설정 안내가 `infra/README.md` 링크로 연결되는지 확인한다.
- 운영자가 `.env` 값을 찾을 때 `infra/README.md`만으로 필요한 키를 찾을 수 있는지 확인한다.
- 두 README에 동일 설정이 중복 기재되어 있지 않은지 확인한다.

## Related Tests
- 자동 테스트 코드: 없음 (문서 구조 검토)
