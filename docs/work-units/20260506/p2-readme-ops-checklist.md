# Work Unit: p2-readme-ops-checklist

## Problem
- 운영 점검 항목이 인증 중심으로 치우쳐 있었고, 실제 배포 시 바꿔야 하는 설정값 기준으로 보기 어려웠음.

## Decision
- README 체크리스트를 “운영 배포 시 확인/변경이 필요한 설정값” 중심으로 재정리.

## Trade-offs
- 장점: 배포 직전 실제 변경 포인트만 빠르게 확인 가능.
- 단점/리스크: 런타임 점검(헬스/대시보드 확인)은 별도 운영 절차 문서에서 보완 필요.

## Implementation Summary
- 변경 파일:
  - `README.md`
- 핵심 반영:
  - 불필요한 운영 절차 항목을 제거하고, 환경변수/포트 노출 등 설정값 점검 항목 위주로 체크리스트 재작성.
  - 각 항목에 짧은 운영값 예시를 추가해 즉시 적용 가능하도록 가독성 개선.

## How To Test
- README의 `운영 체크리스트(핵심, DB/WAS 포함)`가 “값을 확인/변경해야 하는 항목”만 포함하는지 확인한다.
- 체크리스트 각 항목이 `infra/.env.example`, `infra/docker-compose.yml`, `apps/api/src/main/resources/application-local.yml`의 실제 설정 키와 일치하는지 확인한다.

## Related Tests
- 자동 테스트 코드: 문서 변경 작업이라 해당 없음
