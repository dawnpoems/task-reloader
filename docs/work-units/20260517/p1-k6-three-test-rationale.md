# Work Unit: p1-k6-three-test-rationale

## Problem
- 운영 안정성 검증을 명확히 설명하기 위해, 테스트를 `용량(throughput)`, `경합(contention)`, `지속성(drift)` 3축으로 구조화할 필요가 있었다.
- 테스트 설계 의도와 실행 순서를 문서화해, 단순 수치 나열이 아니라 “어떤 운영 가설을 어떤 순서로 검증했는지”를 전달해야 했다.

## Decision
- k6 검증 범위를 아래 3개 테스트로 고정한다.
1. Read Matrix (용량 검증)
2. Mixed Peak (경합 검증)
3. Soak (지속성 검증)

- 실행 순서는 `Read Matrix → Mixed Peak → Soak`로 고정한다.
- 세 테스트는 “낮은 준비 비용 대비 높은 설명력” 기준으로 선정한다.

## Why These 3 Tests

### 1) Read Matrix
- 목적: 시스템이 부하 단계 상승(`5→20→40→60→80 VU`)에서 처리량과 지연시간을 안정적으로 유지하는지 확인
- 이유: 성능 논의의 출발점은 항상 용량 곡선이며, baseline 없이 다른 테스트 해석이 어렵다.
- 현재 결론: read API 기준 `80 VU`, 실패율 `0%`, `p95 8.11ms`로 안정적

### 2) Mixed Peak
- 목적: read/write 동시 처리 시 row lock, DB pool, 트랜잭션 경합에 의한 성능 저하를 확인
- 이유: 실제 운영 트래픽은 read-only가 아니라 write가 섞이며, 경합은 read 테스트에서 잘 드러나지 않는다.
- 구성: `infra/load/k6-auth-mixed-peak-local.js` (`read:write ~70:30`, staged peak)

### 3) Soak
- 목적: 장시간 실행에서 지연시간/에러율/메모리 추세가 악화되는지 확인
- 이유: 단기 테스트는 통과해도 장시간 누적 시 GC, 캐시, connection leak 성격 이슈가 발생할 수 있다.
- 구성: `infra/load/k6-auth-soak-local.js` (`SOAK_VUS=60`, `SOAK_DURATION=2h` 기본)

## Trade-offs
- 장점:
  - 3개 테스트만으로도 운영 안정성 관점의 핵심 축을 설명할 수 있다.
  - 스크립트 재사용성과 문서 일관성이 높아 결과 전달력이 좋아진다.
- 단점/리스크:
  - 장애 주입(Chaos), 멀티노드, 외부 네트워크 품질 변동까지는 포함하지 못한다.
  - 현재 환경이 로컬 단일 서버 중심이라 운영 환경 절대치와 1:1 대응은 어렵다.

## What To Add Next (If Time Allows)

우선순위는 “추가 비용 대비 설명력” 기준으로 정한다.

1. Spike Recovery 테스트 (우선순위: 높음)
- 이유: 급증 후 정상 복귀 시간은 운영 대응력을 설명하는 강한 지표다.
- 시나리오 예: `20 VU 5m → 120 VU 2m → 20 VU 10m`
- 확인 지표: 복귀 시간, spike 구간의 5xx/401/403, p99 급등 폭

2. Dataset Scale 테스트 (우선순위: 중간)
- 이유: 데이터 증가에 따른 쿼리 비용 상승 여부를 조기에 파악 가능
- 시나리오 예: 데이터셋 `x1/x10/x50`에서 동일 부하 비교
- 확인 지표: endpoint별 p95/p99 변화율, DB wait/lock 추세

3. Auth Churn 테스트 (우선순위: 중간)
- 이유: 토큰 갱신/로그인 경로의 병목은 실사용에서 빈번히 문제화됨
- 시나리오 예: 짧은 TTL + 재로그인 빈도 증가
- 확인 지표: `/api/auth/login` p95, 401 비율, 재시도 성공률

## Implementation Summary
- 신규 스크립트:
  - `infra/load/k6-auth-mixed-peak-local.js`
  - `infra/load/k6-auth-soak-local.js`
- 문서:
  - `README.md` (메인: 간결 요약)
  - `infra/load/results/local-read-matrix-20260512-102647/k6-next-test-plan.md` (실행 계획)

## Acceptance Criteria
- 운영 문서에서 아래 문장을 데이터로 뒷받침할 수 있어야 한다.
  - “Read 경로는 단계 부하에서 안정적이다.”
  - “Read/Write 경합 상황을 별도로 검증했다.”
  - “장시간 운전 시 안정성 드리프트를 점검했다.”
- 메인 README는 간결하게 유지하고, 판단 근거는 본 work-unit 문서에서 상세 확인 가능해야 한다.
