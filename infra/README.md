# Task Reloader - Infra & Monitoring

Task Reloader의 인프라 실행/관측성 구성을 빠르게 확인하기 위한 문서입니다.

## 빠른 시작 (Docker 전체 실행)

1. 환경 파일 생성

```bash
cp infra/.env.example infra/.env
```

2. 필요한 값 설정 (`infra/.env`)

```env
POSTGRES_USER=task_reloader
POSTGRES_PASSWORD=change_me_in_production
POSTGRES_DB=task_reloader

SPRING_DATASOURCE_USERNAME=task_reloader
SPRING_DATASOURCE_PASSWORD=change_me_in_production

GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin
```

3. 전체 스택 실행

```bash
cd infra
docker compose up -d --build
```

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
- 기본 대시보드: `Task Reloader - API Overview`

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
