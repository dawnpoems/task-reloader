# Grafana Alert Reduce Expression Fix

## 문제

Grafana Alert Rule 상세 화면에서 `failed to parse expression 'B': setting replaceWithValue must be specified when mode is 'replaceNN'` 오류가 발생했다.
이 오류 때문에 API scrape 값은 정상(`up=1`)이어도 rule evaluation health가 `error`가 되고, `API Down`은 `execErrState: Alerting` 설정 때문에 계속 firing 상태로 남을 수 있었다.

## 작업 내용

- 세 alert rule의 reduce expression 설정에 `replaceWithValue: 0`을 추가했다.
- Grafana alert rule health error troubleshooting 항목을 `infra/README.md`에 추가했다.

## 테스트 방법

- 홈서버에서 `cd infra && docker compose restart grafana`로 Grafana를 재시작한다.
- Grafana `Alerting > Alert rules`에서 `API Down`, `5xx Error Rate High`, `p95 Latency High`의 Health가 `ok`로 돌아오는지 확인한다.
- `API Down`의 evaluation result에서 `A=1`, `B=1`, threshold가 false로 평가되어 `Normal`로 전환되는지 확인한다.

## 관련 테스트

- `ruby -e 'require "yaml"; YAML.load_file("infra/monitoring/grafana/provisioning/alerting/task-reloader-rules.yml")'`
