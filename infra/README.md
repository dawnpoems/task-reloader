# Task Reloader - 인프라 설정

## 🚀 빠른 시작

### 1단계: 환경 설정

`.env` 파일을 생성하세요:

```bash
cp infra/.env.example infra/.env
```

`.env` 파일을 열어 설정값을 수정하세요 (프로덕션 환경에서는 강력한 비밀번호 사용):

```env
POSTGRES_USER=task_reloader
POSTGRES_PASSWORD=your_secure_password      # ⚠️ 변경 필수
POSTGRES_DB=task_reloader

SPRING_DATASOURCE_USERNAME=task_reloader
SPRING_DATASOURCE_PASSWORD=your_secure_password  # ⚠️ 변경 필수
```

---

## 🖥️ 개발 환경 (로컬)

각 앱을 개별적으로 실행하는 방식입니다.

### 1. DB만 Docker로 실행

```bash
cd infra
docker-compose up postgres -d
```

### 2. API 실행 (Spring Boot)

```bash
# 루트에서
./gradlew :apps:api:bootRun --args='--spring.profiles.active=local'
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

### 3. Web 실행 (Vite dev server)

```bash
cd apps/web
npm install
npm run dev
```

- Web: http://localhost:5173
- `/api` 요청은 자동으로 `localhost:8080`으로 프록시됨 (vite.config.ts)

---

## 🐳 운영 환경 (Docker 전체 스택)

모든 서비스(DB + API + Web)를 Docker로 실행합니다.

```bash
cd infra
docker-compose up --build -d
```

| 서비스 | URL |
|--------|-----|
| Web (nginx) | http://localhost:3000 |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |
| PostgreSQL | localhost:5432 |

Grafana 기본 계정은 `.env`의 `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`를 사용합니다.

### 종료

```bash
cd infra
docker-compose down

# 볼륨(DB 데이터)까지 삭제
docker-compose down -v
```

### 모니터링 확인

```bash
# Prometheus 지표 확인
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'

# API가 지표를 노출하는지 확인
curl -s http://localhost:8080/actuator/prometheus | head -n 20
```

Grafana 접속 후:
- URL: `http://localhost:3001`
- 대시보드: `Task Reloader - API Overview` (자동 프로비저닝)

### 권장 임계치 (홈서버/포트폴리오 기준)

| 지표 | Warning | Critical | 확인 창 |
|------|---------|----------|---------|
| 5xx 에러율 | 1% 초과 | 3% 초과 | 5분 |
| p95 Latency | 300ms 초과 | 800ms 초과 | 5분 |
| API 가용성(`up`) | - | 0 (DOWN) | 2분 |

참고 PromQL:

```promql
# 5xx 에러율 (%)
100 * (
  sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*|/healthz|/favicon.ico",status=~"5.."}[5m]))
  /
  clamp_min(sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*|/healthz|/favicon.ico"}[5m])), 0.000001)
)

# p95 latency (sec)
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket{uri!~"/actuator.*|/healthz|/favicon.ico"}[5m])) by (le)
)

# API down
up{job="task-reloader-api"} == 0
```

---

## 🧪 테스트

### API 단위/통합 테스트

```bash
# DB 없이 단위 테스트만
./gradlew :apps:api:test

# Testcontainers 포함 전체 테스트 (Docker 필요)
./gradlew :apps:api:test --info
```

### Web 타입 체크

```bash
cd apps/web
npm run type-check
```

---

## 📋 서비스 아키텍처

```
[Browser]
    │
    ▼
[web: nginx :80]
    │  /         → React SPA (정적 파일)
    │  /api/**   → proxy
    ▼
[api: Spring Boot :8080]
    │
    ▼
[postgres: PostgreSQL :5432]
```

---

## 🐛 문제 해결

### .env 파일 오류
**해결**: `infra/.env` 파일이 생성되었는지 확인하세요.

### PostgreSQL 연결 실패
**해결**: Docker Desktop이 실행 중인지 확인하세요.

### API 헬스체크 실패
**해결**: API가 완전히 시작될 때까지 대기 (약 60초)

---

## 🔒 보안 주의사항

⚠️ **절대 `infra/.env` 파일을 Git에 커밋하지 마세요!**
- `.env`는 `.gitignore`에 등록되어 있습니다
- `.env.example`은 참고용 템플릿입니다


## 🚀 빠른 시작

### 1단계: 환경 설정

`.env` 파일을 생성하세요:

```bash
cp .env.example .env
```

`.env` 파일을 열어 설정값을 수정하세요 (프로덕션 환경에서는 강력한 비밀번호 사용):

```env
POSTGRES_USER=task_reloader
POSTGRES_PASSWORD=your_secure_password      # ⚠️ 변경 필수
POSTGRES_DB=task_reloader

SPRING_DATASOURCE_USERNAME=task_reloader
SPRING_DATASOURCE_PASSWORD=your_secure_password  # ⚠️ 변경 필수
```

### 2단계: Docker Compose 실행

프로젝트 루트에서:

```bash
# 옵션 1: 스크립트로 실행 (권장)
./start.sh

# 옵션 2: 직접 실행
cd infra
docker-compose up --build
```

### 3단계: 서비스 확인

- **PostgreSQL**: http://localhost:5432
- **Spring Boot API**: http://localhost:8080
- **API 문서**: http://localhost:8080/swagger-ui.html

## 📋 서비스 상세 정보

### PostgreSQL
- **포트**: 5432
- **사용자**: `${POSTGRES_USER}`
- **데이터베이스**: `${POSTGRES_DB}`
- **헬스체크**: pg_isready 커맨드 (10초 간격)

### Spring Boot API
- **포트**: 8080
- **빌드**: Multi-stage Dockerfile (최적화된 이미지)
- **헬스체크**: actuator/health 엔드포인트 (30초 간격)
- **시작 대기**: 60초 (애플리케이션 초기화)

## 🛑 종료

```bash
docker-compose down

# 볼륨 포함 삭제 (데이터 제거)
docker-compose down -v
```

## 🐛 문제 해결

### .env 파일 오류
```
Error: Missing environment variable POSTGRES_USER
```
**해결**: `.env` 파일이 생성되었는지 확인하세요.

### PostgreSQL 연결 실패
```
ERROR: pg_isready: could not translate host name "postgres" to address
```
**해결**: Docker Desktop이 실행 중인지 확인하세요.

### API 헬스체크 실패
```
WARN: healthcheck failed
```
**해결**: API가 완전히 시작될 때까지 대기 (약 60초)

## 🔒 보안 주의사항

⚠️ **절대 `.env` 파일을 Git에 커밋하지 마세요!**
- `.env`는 `.gitignore`에 등록되어 있습니다
- `.env.example`은 참고용 템플릿입니다
- 프로덕션 환경에서는 강력한 비밀번호를 사용하세요

## 📚 추가 정보

### Flyway 마이그레이션
- 자동 실행됨 (SQL 파일: `src/main/resources/db/migration/`)
- 파일명 형식: `V{버전}__{설명}.sql`

### Multi-stage Docker Build
- **Stage 1**: JDK 이미지로 빌드
- **Stage 2**: JRE 이미지로 실행 (이미지 크기 최소화)
