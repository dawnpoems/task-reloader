# Grafana Minimum Operational Alert Rules

## 문제

Grafana Alerting contact point와 notification policy는 준비되었지만, 실제 운영 상태를 평가하는 alert rule이 아직 없었다.
홈서버 운영에서는 너무 민감한 알림이 반복되면 확인 피로가 커지므로, 조금 늦게 알더라도 노이즈를 줄이는 기준이 필요했다.

## 결정

- Grafana-managed alert rule을 파일 provisioning으로 추가한다.
- rule 파일은 contact point/policy 파일과 분리해 `task-reloader-rules.yml`로 관리한다.
- 초기 rule은 `API Down`, `5xx Error Rate High`, `p95 Latency High` 3개만 둔다.
- 5xx와 p95는 요청이 거의 없는 시간대의 노이즈를 줄이기 위해 최소 요청률 조건을 함께 둔다.

## 트레이드오프

- 알림 피로는 줄어들지만, 짧은 장애나 낮은 트래픽에서 발생한 단발 오류는 바로 메일로 오지 않을 수 있다.

## 작업 내용

- `task-reloader-operational` rule group 추가
- `API Down`: API scrape target이 3분 이상 down이면 critical 알림
- `5xx Error Rate High`: 요청률이 있는 상태에서 5xx 비율이 10분 이상 10%를 넘으면 critical 알림
- `p95 Latency High`: 요청률이 있는 상태에서 p95 latency가 15분 이상 2초를 넘으면 warning 알림
- `infra/README.md`에 기본 alert rule 기준과 운영 조정 방법 추가

## 테스트 방법

- `cd infra && docker compose up -d grafana`로 Grafana를 재시작한다.
- Grafana `Alerting > Alert rules`에서 `Task Reloader / task-reloader-operational` rule group이 보이는지 확인한다.
- 평상시 `API Down`, `5xx Error Rate High`, `p95 Latency High`가 `Normal` 또는 데이터 상태에 맞게 표시되는지 확인한다.
- 테스트 firing이 필요하면 임계값이나 `for`를 임시로 낮춘 뒤, 테스트 종료 후 운영값으로 되돌린다.

## 관련 테스트

- `docker compose --env-file .env.example config --quiet`
- `ruby -e 'require "yaml"; YAML.load_file("infra/monitoring/grafana/provisioning/alerting/task-reloader-rules.yml")'`
