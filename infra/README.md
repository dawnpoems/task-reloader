# Task Reloader - 인프라 설정

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


