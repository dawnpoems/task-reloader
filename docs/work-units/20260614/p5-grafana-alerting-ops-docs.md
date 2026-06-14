# Grafana Alerting Ops Docs

## 작업 내용

- `infra/README.md`에 Grafana Alerting 변경 후 검증 루틴을 추가했다.
- Contact point, notification policy, alert rule, 테스트 메일 발송까지 확인하는 순서를 정리했다.
- `docs/architecture.md`의 관측성 설명과 다이어그램에 Grafana Alerting과 이메일 알림 흐름을 반영했다.
- 관련 파일 목록에 Grafana alerting provisioning 파일과 rule 파일을 추가했다.

## 테스트 방법

- Grafana alerting 파일을 수정한 뒤 README의 검증 루틴을 따라간다.
- Grafana `Alerting > Contact points`에서 `task-reloader-email`이 보이는지 확인한다.
- Grafana `Alerting > Alert rules`에서 `Task Reloader / task-reloader-operational` rule group이 보이는지 확인한다.
- 테스트 메일 발송 실패 시 README의 SMTP 확인 순서대로 점검한다.

## 관련 테스트

- 문서 변경이라 별도 자동 테스트는 추가하지 않았다.
