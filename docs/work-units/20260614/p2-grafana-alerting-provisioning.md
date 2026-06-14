# Grafana Alerting Provisioning

## 작업 내용

- Grafana alerting provisioning 파일을 추가해 이메일 contact point와 기본 notification policy를 파일로 관리하도록 했다.
- Grafana SMTP 설정을 앱 메일 설정과 분리된 `GRAFANA_SMTP_*` 환경 변수로 매핑했다.
- `GRAFANA_ALERT_EMAIL_TO`로 운영 알림 수신자를 설정할 수 있게 했다.
- `infra/.env.example`과 `infra/README.md`에 Grafana Alerting 메일 설정과 재시작 방법을 추가했다.

## 테스트 방법

- `infra/.env`에서 `GRAFANA_SMTP_ENABLED`, `GRAFANA_SMTP_HOST`, `GRAFANA_ALERT_EMAIL_TO`를 설정한다.
- `cd infra && docker compose up -d grafana`로 Grafana를 재시작한다.
- Grafana `Alerting > Contact points`에서 `task-reloader-email` contact point가 보이는지 확인한다.
- Grafana `Alerting > Notification policies`에서 기본 receiver가 `task-reloader-email`인지 확인한다.

## 관련 테스트

- `docker compose --env-file .env.example config`로 Compose 설정 렌더링을 확인한다.
