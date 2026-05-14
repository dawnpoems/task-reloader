# Grafana 대시보드 확장 권고

본 문서는 현재 구성(`Task Reloader - API Overview`)을 기준으로, 성능 원인분석 역량을 높이기 위한 확장안을 정리한 문서입니다.

## 1) 현재 구성의 장점과 한계

- 장점: `RPS`, `5xx`, `p95`, 상태코드 분포, 느린 API를 빠르게 확인하기에 적합
- 한계: `tail latency(1초대 outlier)`의 원인을 JVM/DB/호스트 레벨로 역추적하기에는 지표 범위가 제한적

## 2) 우선 추가 대시보드

| 대시보드 | 핵심 패널 | 목적 |
|---|---|---|
| API SLO Dashboard | p50/p90/p95/p99, 4xx/5xx, endpoint별 RPS | 사용자 체감 성능과 오류를 한 화면에서 관리 |
| Tail Latency Dashboard | endpoint별 p99/p99.9, latency heatmap | 희귀 지연(outlier) 구간의 재현/분석 |
| Auth & Status Dashboard | `/api/auth/login` 지연, 401/403 시계열, endpoint별 상태코드 | 인증/권한 이슈와 정상 트래픽 분리 |
| JVM Runtime Dashboard | Heap/OldGen, GC pause, Thread, Process CPU | 애플리케이션 런타임 병목 파악 |
| DB & Pool Dashboard | Hikari active/idle/pending, DB connection/lock/deadlock/query latency | DB 병목 및 커넥션 포화 탐지 |
| Infra Saturation Dashboard | Host/Container CPU, iowait, Memory pressure, Disk I/O latency | 시스템 자원 포화 여부 확인 |

## 3) 즉시 추가 가능한 패널 (현 스크랩 기준)

현재 Prometheus는 `task-reloader-api(/actuator/prometheus)` 중심으로 수집 중이므로, 아래 패널부터 우선 적용하는 것을 권장합니다.

1. 전체 `p99` latency + endpoint별 `p99` TopN
2. `4xx` 에러율 및 `401/403` 전용 시계열
3. endpoint별 `RPS` TopN (method+uri)
4. latency histogram/heatmap (`http_server_requests_seconds_bucket` 기반)
5. 시간대별 `p95`-`p99` 갭(꼬리 지연 확장 신호)

## 4) 수집기 확장 후 권장 패널

아래는 exporter 추가 이후 구성 권장 항목입니다.

- `node-exporter`: 호스트 CPU, load, iowait, 메모리 pressure
- `cAdvisor`: 컨테이너 CPU throttling, 메모리 working set, OOM 징후
- `postgres-exporter` (+ `pg_stat_statements`): DB lock/deadlock, slow query, cache hit ratio

## 5) 운영 원칙

1. `SLO 보드`와 `원인분석 보드`를 분리 운영
2. 동일 시간 범위에서 k6 결과와 Grafana 패널 교차 검증
3. 테스트 종료 시 대시보드 스냅샷과 `summary.json` 동시 보관
4. k6 임계치(Threshold)와 Grafana Alert 조건의 기준 정합성 유지
