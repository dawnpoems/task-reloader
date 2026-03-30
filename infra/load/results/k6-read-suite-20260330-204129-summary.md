# k6 Read Suite 결과 요약

- 실행 시각(KST): 2026-03-30 20:41:29
- 대상: `http://localhost:8080`
- 스크립트: `infra/load/k6-read-suite.js`
- 원본 결과(JSON): `infra/load/results/k6-read-suite-20260330-204129.json`

## 실행 설정

- VUs: 10
- Duration: 1m

## 전체 결과

- Iterations: 580
- HTTP requests: 4,060 (67.36 req/s)
- 실패율(`http_req_failed`): 0.00%
- 체크 성공률(`checks`): 8,120 / 8,120 (100.00%)
- 전체 지연시간(`http_req_duration`): avg 5.34ms / p95 11.92ms

## 엔드포인트별 p95

| Endpoint tag | API | p95(ms) | SLO | 판정 |
|---|---|---:|---:|---|
| `tasks_due_now` | `GET /api/tasks?status=DUE_NOW` | 15.37 | < 800 | PASS |
| `tasks_upcoming` | `GET /api/tasks?status=UPCOMING` | 7.42 | < 800 | PASS |
| `insights_dashboard` | `GET /api/insights/dashboard` | 8.94 | < 1000 | PASS |
| `insights_overview` | `GET /api/insights/overview?days=30&top=5` | 9.35 | < 1000 | PASS |
| `insights_recent` | `GET /api/insights/recent-completions` | 8.92 | < 1000 | PASS |
| `task_detail` | `GET /api/tasks/{id}` | 5.69 | < 1000 | PASS |
| `task_completions_monthly` | `GET /api/tasks/{id}/completions?year=YYYY&month=MM` | 6.52 | < 1000 | PASS |

## 메모

- 이번 실행에서는 모든 GET API가 설정한 기준을 만족했습니다.
- 상세/완료이력 API는 데이터가 있는 Task를 찾았을 때만 호출되며, 이번 실행에서는 정상 호출되었습니다.
