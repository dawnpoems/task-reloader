# Work Unit: p0-demo-account-daily-reset

## Problem
- 데모 계정이 공개되면 Task/완료이력이 빠르게 오염되고, 방문자마다 다른 상태를 보게 되어 체험 품질이 떨어진다.
- 데모 비밀번호를 주기적으로 바꾸는 경우 기존 세션이 살아 있으면 실질적인 통제가 어렵다.

## Decision
- 데모 계정 데이터를 스케줄러로 매일 자동 초기화한다.
- 초기화 시 세션을 강제 만료하고, Task를 비운 뒤 샘플 Task를 재생성한다(옵션으로 비활성화 가능).

## Trade-offs
- 장점: 데모 환경을 매일 일정한 기준 상태로 되돌려 체험 신뢰성을 유지할 수 있다.
- 단점/리스크: 리셋 시점에는 데모 사용자 작업 내용이 보존되지 않으며, 운영자가 의도한 데모 시나리오가 아닌 데이터는 사라진다.

## Implementation Summary
- 변경 파일:
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/TaskReloaderApiApplication.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/task/service/DemoAccountDataResetScheduler.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/task/repository/TaskRepository.java`
  - `apps/api/src/main/java/com/yegkim/task_reloader_api/task/repository/TaskCompletionRepository.java`
  - `apps/api/src/main/resources/application-local.yml`
  - `infra/.env.example`
  - `infra/docker-compose.yml`
  - `apps/api/src/test/java/com/yegkim/task_reloader_api/task/service/DemoAccountDataResetSchedulerTest.java`
- 핵심 반영:
  - 스케줄링 활성화(`@EnableScheduling`) 및 조건부 스케줄러(`DEMO_ACCOUNT_RESET_ENABLED=true`일 때만 동작) 추가.
  - 데모 이메일 조회 후:
    - 활성 refresh token 전부 `revoked_at` 처리.
    - 사용자 Task 전부 삭제(완료이력은 FK cascade로 정리).
    - `DEMO_ACCOUNT_RESET_SEED_ENABLED=true`일 때 샘플 Task 6개 재생성.
  - `.env`/compose에 리셋 제어 키 추가:
    - `DEMO_ACCOUNT_RESET_ENABLED`
    - `DEMO_ACCOUNT_RESET_EMAIL`
    - `DEMO_ACCOUNT_RESET_CRON`
    - `DEMO_ACCOUNT_RESET_ZONE_ID`
    - `DEMO_ACCOUNT_RESET_SEED_ENABLED`
  - 회귀 방지 단위테스트 추가:
    - 사용자 없음 스킵
    - seed on: 토큰 만료 + 삭제 + 샘플 생성
    - seed off: 삭제만 수행
    - 이메일 정규화(공백/대소문자)

## How To Test
- 리셋 활성화 상태에서 데모 계정으로 Task/완료이력을 만든 뒤, 스케줄 시각 이후 초기화되는지 확인한다.
- 리셋 직후 기존 데모 세션의 refresh가 거부되어 재로그인이 필요한지 확인한다.
- `DEMO_ACCOUNT_RESET_SEED_ENABLED=true`일 때 샘플 Task가 다시 채워지고, `false`일 때 비어 있는지 확인한다.
- 스케줄러 로그에 리셋 전후 카운트(활성 Task/총 Task/완료이력/폐기 토큰/생성 Task)가 출력되는지 확인한다.

## Related Tests
- `apps/api/src/test/java/com/yegkim/task_reloader_api/task/service/DemoAccountDataResetSchedulerTest.java`
