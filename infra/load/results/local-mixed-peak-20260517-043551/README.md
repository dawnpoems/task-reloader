# Local Mixed Peak 부하테스트 결과 정리

본 문서는 `infra/load/results/local-mixed-peak-20260517-043551` 실행 결과를 기준으로 작성한 성능 분석 보고서입니다.

## 1) 한눈에 보는 결론

- 이번 실행은 `mixed-peak` 시나리오(`0→20→50 VU`, `50 VU 20분 유지`, `50→0 VU`)를 끝까지 수행했습니다.
- 처리량은 `최대 약 600 req/s`, 평균 `415.59 req/s`까지 상승하며 부하 곡선은 시나리오와 정합적으로 움직였습니다.
- 다만 품질 지표는 안정 기준을 충족하지 못했습니다.
  - `http_req_failed = 45.42%`
  - `checks 성공률 = 51.37%`
- Grafana에서도 동일하게 `401`, `429`가 피크 구간에서 크게 증가했고, 일부 `500`이 `GET /api/insights/recent-completions`에 집중되었습니다.
- 즉, 이번 mixed 테스트의 결론은 **"완주 성공"이지만 "운영 안정성 합격"은 아님**입니다.

## 2) 테스트 개요

### 2.1 실행 시각 (KST)

`case-env.txt` 기준 시작 시각:
- 시작: `2026-05-17 13:35:55 KST`

이번 결과 폴더에는 `FINISHED_AT`가 기록되지 않았으므로, 시나리오 길이(`35분`) 기준으로 종료 시각을 계산하면:
- 종료(계산): `2026-05-17 14:10:55 KST`

단계 경계:
- `13:35:55 ~ 13:40:55`: `0 → 20 VU` (5분)
- `13:40:55 ~ 13:45:55`: `20 → 50 VU` (5분)
- `13:45:55 ~ 14:05:55`: `50 VU 유지` (20분)
- `14:05:55 ~ 14:10:55`: `50 → 0 VU` (5분)

### 2.2 실행 환경

| 항목 | 값 |
|---|---|
| CPU | AMD Ryzen 5 PRO 6650H |
| Memory | 16GB |
| Storage | 512GB SSD |
| 대상 URL | `http://127.0.0.1:3000` |
| k6 스크립트 | `infra/load/k6-auth-mixed-peak-local.js` |
| 혼합비 | read:write = `70:30` (`WRITE_RATIO_PERCENT=30`) |

### 2.3 시나리오 동작 방식

- 각 iteration마다 랜덤 분기:
  - `30%`: write flow (`create -> update -> complete -> delete`)
  - `70%`: read flow (`tasks/insights` 중심 batch)
- `sleep(0.5s)` 적용
- 인증은 `setup()` 초기 로그인 + 만료 시 재로그인(`RELOGIN_ON_401=true`)

## 3) Grafana 캡처

### 3.1 대시보드 종합

![Grafana mixed peak overview](./grafana-mixed-peak-overview.png)

### 3.2 5xx Endpoint Top5

![Grafana mixed peak 5xx endpoint top5](./grafana-mixed-peak-5xx-endpoint-top5.png)

## 4) 핵심 수치 요약 (k6 summary)

| 지표 | 값 |
|---|---:|
| 총 요청 수 (`http_reqs.count`) | 872,832 |
| 평균 RPS (`http_reqs.rate`) | 415.59 req/s |
| 총 iterations | 160,038 |
| 전체 avg latency | 1.78 ms |
| 전체 p95 latency | 4.10 ms |
| 전체 p99 latency | 6.02 ms |
| 전체 max latency | 1219.76 ms |
| HTTP 실패율 (`http_req_failed`) | 45.42% |
| checks 성공률 | 51.37% |

### 4.1 flow 기준 latency

| Metric | Count | avg | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|
| `http_req_duration{flow:read}` | 669,508 | 1.46 ms | 2.53 ms | 3.31 ms | 269.82 ms |
| `http_req_duration{flow:write}` | 118,484 | 3.90 ms | 6.47 ms | 8.56 ms | 1219.76 ms |

### 4.2 endpoint 기준 p95 (요약)

| Endpoint | Count | p95 |
|---|---:|---:|
| `tasks_due_now` | 112,042 | 2.60 ms |
| `tasks_upcoming` | 112,042 | 2.61 ms |
| `insights_dashboard` | 112,042 | 2.64 ms |
| `insights_overview` | 112,042 | 2.59 ms |
| `insights_recent` | 112,042 | 2.53 ms |
| `task_create` | 47,996 | 7.55 ms |
| `task_update` | 23,496 | 5.88 ms |
| `task_complete` | 23,496 | 5.92 ms |

## 5) 그래프 기반 인사이트

### 5.1 요청량(RPS)

- `0→20→50 VU` 구간에서 RPS가 빠르게 증가하여 피크 `~600 req/s`에 도달했습니다.
- `50 VU 유지` 구간에서 `~500 req/s` 내외로 유지되다가, ramp-down에 맞춰 하강했습니다.
- 시나리오 단계 변화와 RPS 곡선은 전반적으로 일치합니다.

### 5.2 에러율(5xx)

- 전체 5xx 비율은 매우 낮은 수준(그래프 축 기준 `0.04%` 피크 근처)입니다.
- 그러나 "5xx가 낮다"는 사실만으로 안정적이라고 볼 수는 없습니다.
- 실제 실패의 주원인은 5xx보다 `401`, `429`였습니다.

### 5.3 p95 latency

- p95는 대부분 `3~5ms` 수준으로 낮게 보입니다.
- 하지만 이 구간에는 `401/429` 빠른 실패 응답이 많이 섞여 있어, latency 수치가 좋아 보여도 성공률이 낮을 수 있습니다.
- 따라서 mixed 테스트에서는 latency와 함께 `status code 분포`, `http_req_failed`, `checks`를 반드시 같이 봐야 합니다.

### 5.4 상태코드 분포

대시보드에서 확인된 핵심 패턴:
- 정상 응답(200 계열) 비중이 피크 구간에서 감소
- 동시에 `401`이 큰 비중으로 상승
- `429`도 동반 상승
- `500`은 소량

해석:
- 서버 처리 성능 자체보다 인증/보호 정책 구간에서 먼저 병목이 발생한 흐름입니다.

### 5.5 5xx Endpoint Top5

- `500 GET /api/insights/recent-completions`가 관찰되며, 피크 시점은 대략 `13:50` 전후입니다.
- 절대량은 낮지만, 특정 endpoint로 모여 나타난다는 점에서 원인 추적 대상입니다.

## 6) 원인 추정 (데이터 근거)

`root_group.checks` 집계를 보면 인증 bootstrap 체크 실패가 매우 큽니다.

| Check | Passes | Fails |
|---|---:|---:|
| `auth bootstrap: status 200` | 25 | 84,814 |
| `tasks_due_now: status 200` | 54,649 | 57,393 |
| `tasks_upcoming: status 200` | 54,649 | 57,393 |
| `task_create: status 201` | 23,496 | 24,500 |

추정:
- 토큰 재발급(re-login) 시도 실패가 대량 발생
- 이후 read/write endpoint에서 `401/429`가 연쇄적으로 증가
- 결과적으로 성공률이 크게 하락

## 7) 이번 실행의 의미

- 장점:
  - mixed 시나리오를 실제로 완주했고, 단계별 부하 패턴 재현이 가능함을 확인
  - RPS/지연/상태코드/5xx endpoint를 한 번에 연동해 관측 가능한 상태를 확보
- 한계:
  - 현재 설정에서는 인증 재시도/제한정책 구간이 mixed 안정성의 병목으로 확인됨
  - 따라서 "read API 성능"과 "운영 안정성"을 동일선상에서 해석하면 왜곡될 수 있음

## 8) 우선 개선 포인트

1. 인증 재시도 전략 점검
- 토큰 TTL, 만료 전 갱신 시점, `RELOGIN_ON_401` 동작, 동시 재로그인 폭주 여부 점검

2. `401/429` 원인 분리
- rate-limit 정책 값과 인증 실패 원인을 endpoint/시간대 기준으로 분리 집계

3. endpoint 예외 추적
- `GET /api/insights/recent-completions`의 500 발생 시점에 맞춰 API/DB 로그 상관분석

4. mixed 기준 운영 합격선 재정의
- latency만이 아니라 `성공률(checks)`, `http_req_failed`, `401/429 비중`을 합격 기준에 포함

## 9) 원본 데이터 위치

- 실행 루트: `infra/load/results/local-mixed-peak-20260517-043551`
- k6 요약: `mixed-peak/summary.json`
- 요약 테이블: `k6-summary.tsv`, `k6-summary.txt`
- 실행 환경: `test-env.txt`, `mixed-peak/case-env.txt`
- Grafana 캡처: `grafana-mixed-peak-overview.png`, `grafana-mixed-peak-5xx-endpoint-top5.png`

## 10) 결과 해석 주의

- `summary.json`의 `thresholds` boolean은 본 결과에서도 직관과 다르게 보일 수 있습니다.
- 최종 판단은 아래 실측값 기반으로 진행했습니다.
  - `http_req_failed.value`
  - `checks.value`
  - endpoint별 `p95`
  - Grafana 상태코드/에러 시계열
