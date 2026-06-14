# Grafana Alert Message And Label Polish

## 작업 내용

- 기본 alert rule 3종에 `environment=home` label을 추가했다.
- 각 rule을 `Task Reloader - API Overview` 대시보드의 관련 패널과 연결했다.
- 메일에서 바로 판단할 수 있도록 `summary`, `description`, `dashboard`, `runbook`, `threshold` annotation을 보강했다.
- `infra/README.md`에 alert별 1차 확인 위치와 확인 포인트를 추가했다.

## 테스트 방법

- `cd infra && docker compose up -d grafana`로 Grafana를 재시작한다.
- Grafana `Alerting > Alert rules`에서 각 rule의 labels/annotations가 표시되는지 확인한다.
- rule 상세에서 연결된 dashboard/panel로 이동할 수 있는지 확인한다.
- contact point 테스트 또는 임시 firing 테스트 메일에서 annotation이 읽기 좋게 보이는지 확인한다.

## 관련 테스트

- `ruby -e 'require "yaml"; YAML.load_file("infra/monitoring/grafana/provisioning/alerting/task-reloader-rules.yml")'`
