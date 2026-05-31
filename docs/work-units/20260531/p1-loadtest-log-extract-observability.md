# Work Unit: p1-loadtest-log-extract-observability

## 문제

부하테스트에서 5xx가 발생해도 Docker 로그가 이후 테스트나 컨테이너 재생성으로 사라지면 원인 stack trace를 다시 확인하기 어렵다.

이전 `recent-completions` 500 분석에서도 Grafana에는 500이 남아 있었지만, 초기 실행 로그가 남아 있지 않아 재현 직후 다시 로그를 추출해야 했다.

## 결정

k6 case가 끝난 직후 API/DB 컨테이너 로그를 자동 추출하고, 5xx requestId와 stack trace를 결과 폴더에 저장한다.

## 작업 내용

- `infra/load/extract-loadtest-logs.sh` 추가
- `run-matrix.sh`에 case 종료 직후 로그 자동 추출 연동
- k6가 실패 exit code를 반환해도 container stats, 종료 시간, 로그 추출을 먼저 수행하도록 변경
- `case-env.txt`에 `K6_EXIT_CODE` 기록
- `infra/README.md`에 로그 추출 결과 파일과 수동 실행 방법 문서화

## 테스트 방법

- `bash -n infra/load/extract-loadtest-logs.sh`
- `bash -n infra/load/run-matrix.sh`

## 관련 테스트

- 자동화 테스트 없음
- shell 문법 검증으로 스크립트 파싱 확인
