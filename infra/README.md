# Task Reloader - Infra & Monitoring

Task Reloader의 인프라 실행/관측성 구성을 빠르게 확인하기 위한 문서입니다.
운영 실행, `.env` 설정, Cloudflare Tunnel, 모니터링 점검은 이 문서를 단일 기준으로 사용합니다.

## 빠른 시작 (Docker 전체 실행)

1. 환경 파일 생성

```bash
cp infra/.env.example infra/.env
```

2. 필요한 값 설정 (`infra/.env`, 전체 키는 `infra/.env.example` 참고)

```env
POSTGRES_USER=task_reloader
POSTGRES_PASSWORD=change_me_in_production
POSTGRES_DB=task_reloader

SPRING_DATASOURCE_USERNAME=task_reloader
SPRING_DATASOURCE_PASSWORD=change_me_in_production
SPRING_PROFILES_ACTIVE=local

AUTH_ADMIN_EMAIL=admin@task-reloader.local
AUTH_ADMIN_PASSWORD_HASH=__CHANGE_ME_WITH_BCRYPT_HASH__
AUTH_JWT_SECRET=__CHANGE_ME_WITH_AT_LEAST_32_BYTE_SECRET__
AUTH_ACCESS_TOKEN_TTL_SECONDS=900
AUTH_REFRESH_TOKEN_TTL_SECONDS=1209600
AUTH_REFRESH_COOKIE_SECURE=true
AUTH_REFRESH_COOKIE_SAME_SITE=Lax
AUTH_CSRF_COOKIE_SECURE=true
AUTH_CSRF_COOKIE_SAME_SITE=Lax
AUTH_CSRF_ALLOWED_ORIGINS=https://app.task-reloader.example

AUTH_RATE_LIMIT_ENABLED=true
AUTH_RATE_LIMIT_LOGIN_IP_LIMIT=30
AUTH_RATE_LIMIT_LOGIN_IP_EMAIL_LIMIT=5
AUTH_RATE_LIMIT_SIGNUP_IP_LIMIT=10
AUTH_RATE_LIMIT_SIGNUP_IP_EMAIL_LIMIT=3
AUTH_RATE_LIMIT_REFRESH_IP_LIMIT=60

DEMO_ACCOUNT_RESET_ENABLED=false
DEMO_ACCOUNT_RESET_EMAIL=demo@dawnpoem.kr
DEMO_ACCOUNT_RESET_CRON=0 0 4 * * *
DEMO_ACCOUNT_RESET_ZONE_ID=Asia/Seoul
DEMO_ACCOUNT_RESET_SEED_ENABLED=true

MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS_ENABLE=false
TASK_DUE_EMAIL_ALERT_MAIL_FROM=no-reply@task-reloader.local
TASK_RELOADER_APP_URL=http://localhost:3000

GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin
GRAFANA_SMTP_ENABLED=false
GRAFANA_SMTP_HOST=host.docker.internal:1025
GRAFANA_SMTP_USER=
GRAFANA_SMTP_PASSWORD=
GRAFANA_SMTP_FROM_ADDRESS=alerts@task-reloader.local
GRAFANA_SMTP_FROM_NAME=Task Reloader Grafana
GRAFANA_SMTP_SKIP_VERIFY=false
GRAFANA_SMTP_STARTTLS_POLICY=OpportunisticStartTLS
GRAFANA_ALERT_EMAIL_TO=admin@task-reloader.local

# Cloudflare Tunnel (Docker profile)
CLOUDFLARE_TUNNEL_TOKEN=<Cloudflare 대시보드에서 발급한 토큰>
```

3. 전체 스택 실행

```bash
cd infra
docker compose up -d --build
```

## Cloudflare Tunnel (Docker 기반)

`cloudflared`를 컨테이너로 띄우는 경로입니다. (`docker-compose`의 `tunnel` profile 사용)

1. Cloudflare 대시보드에서 Named Tunnel 생성
   - 메뉴: `Zero Trust > Networks > Tunnels`
   - 환경: `Docker`
   - 발급된 토큰을 복사

2. Public Hostname 라우트 추가
   - 예: `app.task-reloader.example`
   - Service URL: `http://web:80`
   - 중요: cloudflared가 컨테이너로 동작하므로 `localhost:3000`이 아니라 같은 Docker 네트워크의 서비스 이름(`web`)을 사용해야 합니다.

3. `infra/.env`에 값 반영
   - `CLOUDFLARE_TUNNEL_TOKEN=<발급 토큰>`
   - `AUTH_CSRF_ALLOWED_ORIGINS=https://app.task-reloader.example`

4. 터널 포함 실행

```bash
cd infra
docker compose --profile tunnel up -d --build
```

5. 연결 상태 확인

```bash
cd infra
docker compose logs -f cloudflared
```

## 운영 보안 설정 체크 (단일 오리진)

- Web과 API는 같은 오리진에서 서비스하고(`/api` reverse proxy), 외부 노출은 HTTPS를 사용합니다.
- `AUTH_CSRF_ALLOWED_ORIGINS`는 실제 Web Origin과 정확히 일치해야 합니다.
- 운영 환경에서 쿠키 보안 플래그는 아래 값을 유지합니다.
  - `AUTH_REFRESH_COOKIE_SECURE=true`
  - `AUTH_CSRF_COOKIE_SECURE=true`
  - `AUTH_REFRESH_COOKIE_SAME_SITE=Lax`
  - `AUTH_CSRF_COOKIE_SAME_SITE=Lax`
- 인증 API 과호출 방어는 기본 활성화(`AUTH_RATE_LIMIT_ENABLED=true`) 상태로 운영합니다.
- 데모 계정을 공개할 때는 자동 초기화를 켜고 주기/시간대를 운영에 맞게 확정합니다.
  - `DEMO_ACCOUNT_RESET_ENABLED=true`
  - `DEMO_ACCOUNT_RESET_ENABLED=true`이면 앱 시작 시 1회 즉시 리셋도 함께 수행됩니다.
  - `DEMO_ACCOUNT_RESET_CRON=0 0 4 * * *`
  - `DEMO_ACCOUNT_RESET_ZONE_ID=Asia/Seoul`
- 로컬 HTTP 테스트 시에는 아래처럼 오버라이드합니다.
  - `AUTH_REFRESH_COOKIE_SECURE=false`
  - `AUTH_CSRF_COOKIE_SECURE=false`
  - `AUTH_CSRF_ALLOWED_ORIGINS=http://localhost:3000`

### 작업 마감 이메일 알림 메일 설정

- 실제 메일 발송을 사용하려면 SMTP 제공자에서 발급받은 값을 `infra/.env`에 반영합니다.
- `local` 프로필로 API를 실행하면 `infra/.env`가 자동으로 반영됩니다.
- 로컬 개발에서 Mailpit/Mailhog 같은 SMTP 캡처 도구를 쓰면 `MAIL_HOST=localhost`, `MAIL_PORT=1025`, 인증/STARTTLS 비활성화로 테스트할 수 있습니다.
- 운영 SMTP에서는 제공자 정책에 맞춰 아래 값을 조정합니다.
  - `MAIL_HOST`
  - `MAIL_PORT`
  - `MAIL_USERNAME`
  - `MAIL_PASSWORD`
  - `MAIL_SMTP_AUTH`
  - `MAIL_SMTP_STARTTLS_ENABLE`
  - `TASK_DUE_EMAIL_ALERT_MAIL_FROM`
  - `TASK_RELOADER_APP_URL`

### 데모 계정 자동 초기화 동작 정리

- `DEMO_ACCOUNT_RESET_ENABLED=false`:
  - 데모 초기화 기능 비활성화 (시작 시/스케줄 모두 동작 안 함)
- `DEMO_ACCOUNT_RESET_ENABLED=true`:
  - 앱 시작 직후 1회 즉시 리셋
  - `DEMO_ACCOUNT_RESET_CRON`, `DEMO_ACCOUNT_RESET_ZONE_ID` 기준 주기 리셋
  - 데모 계정의 등록된 이메일 알림 수신자 삭제
  - 이메일 알림 사용 여부 끄기, 발송 시간 `09:00`, 타임존 `Asia/Seoul`로 초기화
- `DEMO_ACCOUNT_RESET_SEED_ENABLED=true`:
  - 리셋 후 샘플 Task 자동 재생성
- `DEMO_ACCOUNT_RESET_SEED_ENABLED=false`:
  - 리셋 후 샘플 Task 재생성 없이 빈 상태 유지

## 서비스 URL

| 서비스 | URL |
|---|---|
| Web | http://localhost:3000 |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |

## Grafana 사용법

### 로그인

- URL: `http://localhost:3001`
- 계정: `infra/.env`의 `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`

### 자동 프로비저닝

- Datasource: Prometheus (`infra/monitoring/grafana/provisioning/datasources/datasource.yml`)
- Dashboard provider: `infra/monitoring/grafana/provisioning/dashboards/dashboard.yml`
- Dashboard JSON: `infra/monitoring/grafana/dashboards/task-reloader-overview.json`
- Alerting: `infra/monitoring/grafana/provisioning/alerting/task-reloader-alerting.yml`
- Alert rules: `infra/monitoring/grafana/provisioning/alerting/task-reloader-rules.yml`
- 기본 대시보드: `Task Reloader - API Overview`

### Grafana Alerting 메일 설정

- Grafana 운영 알림 메일은 앱의 작업 마감 이메일 설정(`MAIL_*`)과 분리해서 관리합니다.
- 기본 contact point는 `task-reloader-email`이며, 수신자는 `GRAFANA_ALERT_EMAIL_TO`를 사용합니다.
- 로컬 SMTP 캡처 도구를 호스트에서 실행할 때는 기본값 `GRAFANA_SMTP_HOST=host.docker.internal:1025`를 사용할 수 있습니다.
- 실제 메일 발송을 켜려면 `infra/.env`에서 아래 값을 SMTP 제공자에 맞게 설정합니다.
  - `GRAFANA_SMTP_ENABLED=true`
  - `GRAFANA_SMTP_HOST`
  - `GRAFANA_SMTP_USER`
  - `GRAFANA_SMTP_PASSWORD`
  - `GRAFANA_SMTP_FROM_ADDRESS`
  - `GRAFANA_SMTP_FROM_NAME`
  - `GRAFANA_SMTP_SKIP_VERIFY`
  - `GRAFANA_SMTP_STARTTLS_POLICY`
  - `GRAFANA_ALERT_EMAIL_TO`
- 설정 변경 후에는 Grafana 컨테이너를 재시작합니다.

```bash
cd infra
docker compose up -d grafana
```

### 홈서버 Grafana Alerting 운영 체크

- `infra/.env`의 `GRAFANA_SMTP_PASSWORD`는 커밋하지 않습니다. 일반 계정 비밀번호보다 SMTP 전용 토큰이나 앱 비밀번호를 사용합니다.
- `GRAFANA_SMTP_HOST`의 `localhost`는 홈서버 호스트가 아니라 Grafana 컨테이너 자신을 의미합니다.
- macOS/Windows Docker Desktop에서 호스트 SMTP 캡처 도구를 쓰면 `host.docker.internal:1025`를 사용할 수 있습니다.
- Linux 홈서버에서는 `host.docker.internal`이 기본 동작하지 않을 수 있으므로, 실제 SMTP 제공자를 쓰거나 SMTP 캡처 도구를 Compose 서비스로 올린 뒤 서비스 이름을 `GRAFANA_SMTP_HOST`에 사용합니다.
- 운영 SMTP는 제공자 정책에 맞춰 `GRAFANA_SMTP_HOST`, `GRAFANA_SMTP_USER`, `GRAFANA_SMTP_PASSWORD`, `GRAFANA_SMTP_FROM_ADDRESS`를 맞춥니다. 발신 주소가 인증 계정이나 허용된 도메인과 다르면 발송 실패 또는 스팸 분류가 날 수 있습니다.
- 운영에서는 `GRAFANA_SMTP_SKIP_VERIFY=false`를 유지합니다. 자체 서명 인증서 등으로 TLS 검증이 실패하는 임시 상황이 아니면 끄지 않습니다.
- Grafana와 Prometheus host port는 기본값처럼 `127.0.0.1`에만 바인딩합니다. 외부에서 Grafana를 봐야 하면 Cloudflare Access, VPN, SSH tunnel 같은 별도 보호 경로를 사용합니다.
- 이 구성은 Grafana가 알림 평가와 메일 발송을 담당합니다. 홈서버 전원, 네트워크, Docker, Grafana 자체가 내려가면 알림도 발송되지 않습니다. 서버 다운까지 감지하려면 외부 uptime monitor나 Grafana Cloud 같은 외부 관측을 별도로 둡니다.
- 설정 후 Grafana UI에서 `Alerting > Contact points > task-reloader-email` 테스트 발송을 먼저 확인하고, 그 다음 실제 alert rule을 추가합니다.

### 기본 Alert Rule

기본 운영 알림은 잦은 오탐을 줄이기 위해 조금 여유 있는 기준으로 시작합니다.

| Rule | 조건 | 지속 시간 | No data |
|---|---|---:|---|
| API Down | `up{job="task-reloader-api"}`가 `1` 미만 | 3분 | Alerting |
| 5xx Error Rate High | 5xx 비율 `10%` 초과, 전체 요청률 `0.02 rps` 초과 | 10분 | OK |
| p95 Latency High | p95 latency `2s` 초과, 전체 요청률 `0.02 rps` 초과 | 15분 | OK |

- 5xx와 p95 rule은 요청이 거의 없는 시간대의 노이즈를 줄이기 위해 최소 요청률 조건을 함께 둡니다.
- 임계값을 바꾸려면 `task-reloader-rules.yml`을 수정하고 Grafana 컨테이너를 재시작합니다.
- 테스트 firing을 확인할 때는 임계값이나 `for`를 임시로 낮춘 뒤, 테스트가 끝나면 운영값으로 되돌립니다.

### Grafana Alerting 알림별 1차 확인

| Alert | 먼저 볼 곳 | 확인할 것 |
|---|---|---|
| API Down | `docker compose ps`, `/healthz`, `/actuator/prometheus` | API 컨테이너가 떠 있는지, healthcheck가 실패하는지, Prometheus scrape endpoint가 응답하는지 확인 |
| 5xx Error Rate High | Grafana `에러율 (5xx)`, `5xx Endpoint Top5`, API 로그 | 오류가 특정 endpoint에 집중되는지 확인하고 requestId로 예외 로그 추적 |
| p95 Latency High | Grafana `p95 Latency`, `느린 API Top5 (p95)`, `요청량 (RPS)` | 지연이 특정 endpoint에 집중되는지, RPS/5xx 상승과 같이 발생했는지 확인 |

- 모든 alert rule에는 `environment=home`, `service=task-reloader-api`, `category`, `severity` label을 붙입니다.
- Grafana alert rule은 `Task Reloader - API Overview` 대시보드의 관련 패널에 연결합니다.
- 메일의 `summary`, `description`, `threshold`, `dashboard`, `runbook` annotation을 보고 1차 확인 순서를 정합니다.

### 대시보드에서 보는 핵심

- `요청량 (RPS)`: 트래픽 변화/급증 감지
- `에러율 (5xx)`: 장애 징후 감지
- `p95 Latency`: 사용자 체감 성능 저하 감지
- `상태코드별 요청량`: 정상/비정상 비율 확인
- `느린 API Top5`, `5xx Endpoint Top5`: 병목/오류 우선순위 파악

### 점검 루틴 (추천)

1. RPS 상승 구간에서 5xx, p95가 함께 상승하는지 확인
2. `느린 API Top5`의 URI를 기준으로 requestId 로그 추적
3. `5xx Endpoint Top5`에서 오류 집중 엔드포인트 우선 대응

## 모니터링 확인 커맨드

```bash
# Prometheus scrape target 상태
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'

# API Prometheus metrics 노출 확인
curl -s http://localhost:8080/actuator/prometheus | head -n 20
```

## k6 부하 테스트 (스모크)

1. k6 설치 (macOS)

```bash
brew install k6
```

2. 스모크 테스트 실행

```bash
BASE_URL=http://localhost:8080 k6 run infra/load/k6-smoke.js
```

3. 결과 확인 포인트

- `http_req_failed`: 실패율 (기본 목표 `< 1%`)
- `http_req_duration p(95)`: 95퍼센타일 응답시간 (기본 목표 `< 800ms`)
- `checks`: 응답 검증 성공률 (기본 목표 `> 99%`)

### 통합 실행 스크립트 (run-matrix.sh)

`infra/load/run-matrix.sh`로 `read / mixed / soak`를 동일 포맷으로 실행할 수 있습니다.

read matrix만 실행 (기본):

```bash
./infra/load/run-matrix.sh
```

mixed peak만 실행:

```bash
SUITE_MODE=mixed ./infra/load/run-matrix.sh
```

soak만 실행:

```bash
SUITE_MODE=soak SOAK_VUS=60 SOAK_DURATION=2h ./infra/load/run-matrix.sh
```

3개 시나리오 연속 실행:

```bash
SUITE_MODE=all ./infra/load/run-matrix.sh
```

공통 환경변수 예시:

- `BASE_URL` (기본 `http://127.0.0.1:3000`)
- `AUTH_EMAIL`, `AUTH_PASSWORD`
- `MATRIX_NAME` (결과 디렉터리 이름)
- `RESULT_ROOT` (결과 루트, 기본 `infra/load/results`)
- `SUMMARY_AFTER_RUN` (`true|false`, 기본 `true`) 실행 후 `k6-summary.tsv/txt` 자동 생성
- `EXTRACT_LOGS_AFTER_CASE` (`true|false`, 기본 `true`) 각 case 종료 직후 API/DB 로그와 5xx trace 자동 추출

요약만 다시 뽑고 싶으면:

```bash
./infra/load/summarize-k6-result.sh infra/load/results/<result-dir>
```

시작/종료시간 빠른 확인:

```bash
cat infra/load/results/<result-dir>/k6-run-window.txt
cat infra/load/results/<result-dir>/k6-summary.txt
```

### 부하 테스트 로그 자동 추출

`run-matrix.sh`는 각 case가 끝난 직후 `infra/load/extract-loadtest-logs.sh`를 실행해 결과 폴더에 원인분석용 로그를 저장합니다.

생성 위치:

```text
infra/load/results/<result-dir>/<case-dir>/log-extract/
```

주요 생성 파일:

- `api.log`: case 실행 시간대의 API 컨테이너 로그
- `db.log`: case 실행 시간대의 DB 컨테이너 로그
- `access-5xx.log`: 5xx access log
- `access-500.log`: 500 access log
- `access-401-429.log`: 인증/rate-limit 관련 access log
- `5xx-request-ids.txt`: 5xx requestId 목록
- `5xx-request-traces.log`: requestId별 stack trace
- `exception-summary.txt`: 예외 클래스별 집계
- `db-errors.log`: DB error/deadlock/timeout/constraint 관련 로그
- `500-summary.md`: 위 내용을 한 번에 보는 요약 문서

수동으로 다시 추출:

```bash
./infra/load/extract-loadtest-logs.sh infra/load/results/<result-dir>
```

특정 case만 다시 추출:

```bash
./infra/load/extract-loadtest-logs.sh infra/load/results/<result-dir>/mixed-peak
```

로그 추출 시간 범위는 `case-env.txt`의 `STARTED_AT_EPOCH`, `FINISHED_AT_EPOCH`를 기준으로 하며, 기본적으로 앞뒤 180초 여유를 둡니다.

```bash
LOG_EXTRACT_PAD_BEFORE_SEC=300 \
LOG_EXTRACT_PAD_AFTER_SEC=300 \
./infra/load/extract-loadtest-logs.sh infra/load/results/<result-dir>
```

자동 추출을 끄고 싶으면:

```bash
EXTRACT_LOGS_AFTER_CASE=false SUITE_MODE=soak ./infra/load/run-matrix.sh
```

### Grafana 어노테이션 자동 생성 (공용)

`infra/load/create-grafana-annotations.sh`는 mixed 결과 디렉터리에서 실행 시간대를 읽어,  
Grafana에 공용(org) annotation을 한 번에 생성합니다.

- 생성 항목: 시각선 5개 + 구간(region) 4개
- 동일 태그를 조회하는 여러 대시보드에서 재사용 가능
- 기본 시간 정렬: 분 단위 반올림(`ROUND_TO_MINUTE=true`)
- 중복 방지: 동일 이벤트가 있으면 기본 스킵(`SKIP_EXISTING=true`)

미리보기(DRY_RUN):

```bash
DRY_RUN=true ./infra/load/create-grafana-annotations.sh infra/load/results/<mixed-result-dir>
```

실제 생성(토큰 인증):

```bash
GRAFANA_URL=http://127.0.0.1:3001 \
GRAFANA_TOKEN=<grafana_api_token> \
ANNOTATION_TAGS=loadtest,mixed,2026-05-17 \
./infra/load/create-grafana-annotations.sh infra/load/results/<mixed-result-dir>
```

중복 탐지/정리(먼저 DRY_RUN 권장):

```bash
# 리포트만
DRY_RUN=true \
GRAFANA_URL=http://127.0.0.1:3001 \
GRAFANA_USER=<user> \
GRAFANA_PASSWORD=<pass> \
./infra/load/dedupe-grafana-annotations.sh infra/load/results/<mixed-result-dir>

# 실제 삭제 (기본: 가장 오래된 1개 유지, 나머지 삭제)
DRY_RUN=false \
KEEP_MODE=oldest \
GRAFANA_URL=http://127.0.0.1:3001 \
GRAFANA_USER=<user> \
GRAFANA_PASSWORD=<pass> \
./infra/load/dedupe-grafana-annotations.sh infra/load/results/<mixed-result-dir>
```

### Cloudflare 우회(홈서버 localhost 직행) 인증 포함 읽기 부하 테스트

인증이 필요한 현재 운영 구성에서는 로그인 토큰을 먼저 확보한 뒤 읽기 API에 부하를 주는 스크립트를 사용합니다.

권장 기본값:
- `BASE_URL=http://127.0.0.1:3000` (web + nginx `/api` 프록시 경로 포함)
- 또는 `BASE_URL=http://127.0.0.1:8080` (API 직접 성능만 측정)

1) 초기 로그인 1회 + 토큰 공유 방식

```bash
BASE_URL=http://127.0.0.1:3000 \
AUTH_EMAIL=demo@dawnpoem.kr \
AUTH_PASSWORD='demo1234!' \
VUS=20 DURATION=3m \
k6 run infra/load/k6-auth-read-local.js
```

2) 사전 발급 토큰 사용 방식 (로그인 호출 없이 실행)

```bash
BASE_URL=http://127.0.0.1:3000 \
ACCESS_TOKEN='<pre-issued-access-token>' \
VUS=20 DURATION=3m \
k6 run infra/load/k6-auth-read-local.js
```

참고:
- 이 스크립트는 `/api/auth/refresh`를 부하 대상에 포함하지 않습니다.
- 기본적으로 로그인 호출은 `setup()`에서 1회만 수행해 auth rate-limit 영향을 최소화합니다.

### 인증 포함 혼합 부하(Mixed Peak: read+write)

read/write 혼합 부하를 단계적으로 올려 운영 구간 안정성을 확인할 때 사용합니다.

기본 실행(권장):

```bash
BASE_URL=http://127.0.0.1:3000 \
AUTH_EMAIL=demo@dawnpoem.kr \
AUTH_PASSWORD='demo1234!' \
WRITE_RATIO_PERCENT=30 \
k6 run infra/load/k6-auth-mixed-peak-local.js
```

주요 기본값:
- `WARMUP_DURATION=5m`, `WARMUP_VUS=20`
- `RAMP_TO_PEAK_DURATION=5m`, `PEAK_VUS=50`
- `PEAK_HOLD_DURATION=20m`
- `RAMP_DOWN_DURATION=5m`

즉, 기본 부하 패턴은 `0→20(5m) → 20→50(5m) → 50 유지(20m) → 50→0(5m)`입니다.

### 인증 포함 장시간 안정성(Soak: read 중심 + 소량 write)

장시간 실행 중 지연 드리프트/에러율/리소스 추세를 확인할 때 사용합니다.

기본 실행(권장):

```bash
BASE_URL=http://127.0.0.1:3000 \
AUTH_EMAIL=demo@dawnpoem.kr \
AUTH_PASSWORD='demo1234!' \
SOAK_VUS=60 \
SOAK_DURATION=2h \
WRITE_RATIO_PERCENT=15 \
k6 run infra/load/k6-auth-soak-local.js
```

## 로컬 개발 모드 (DB만 Docker)

백엔드/프론트를 IDE/로컬 서버로 실행하고 DB만 Docker로 띄우는 방식입니다.

```bash
cd infra
docker compose up -d postgres
```

- API(local): `http://localhost:8080`
- Web(local): `http://localhost:5173`

## 종료/정리

```bash
cd infra
docker compose down

# DB/Prometheus/Grafana 데이터 볼륨까지 삭제
docker compose down -v
```

## 문제 해결

### Grafana 대시보드가 비어 있을 때

- Prometheus target이 `up`인지 먼저 확인
- API의 `/actuator/prometheus` 응답 확인
- `docker compose logs prometheus grafana api`로 에러 로그 확인

### DB 연결 실패로 API가 시작되지 않을 때

- PostgreSQL 컨테이너 상태 확인: `docker compose ps`
- `infra/.env`의 DB 계정/비밀번호와 API datasource 값이 일치하는지 확인
