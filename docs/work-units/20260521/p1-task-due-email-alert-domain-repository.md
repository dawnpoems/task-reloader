# 작업 단위: p1-task-due-email-alert-domain-repository

## 문제
- 작업 마감 이메일 알림 스키마가 추가되었지만, 애플리케이션 코드에서 해당 테이블을 다룰 JPA 도메인/리포지토리가 아직 없다.
- 다음 단계의 설정 API, 수신자 관리 API, 스케줄러 구현을 위해 설정/수신자/발송로그를 일관된 객체로 읽고 저장할 기반이 필요하다.

## 결정
- 새 패키지 `alert` 아래에 엔티티와 리포지토리를 둔다.
- 기존 `Task` 도메인 스타일에 맞춰 사용자 참조는 `User` 연관관계 대신 `userId` 값으로 관리한다.
- 테이블명과 클래스명은 `TaskDueEmailAlert*` 기준으로 맞춘다.

## 트레이드오프
- 장점: 기존 코드의 사용자 소유 모델과 일관되고, 이후 서비스/API에서 권한 필터링을 단순하게 처리할 수 있다.
- 단점: `User` 엔티티 직접 탐색은 하지 않으므로 사용자 이메일 등 추가 정보가 필요하면 별도 조회가 필요하다.

## 구현 요약
- `TaskDueEmailAlertSetting` 엔티티 추가
- `TaskDueEmailAlertRecipient` 엔티티 추가
- `TaskDueEmailAlertDeliveryLog` 엔티티 및 `TaskDueEmailAlertDeliveryStatus` enum 추가
- 설정/수신자/발송로그별 Spring Data JPA 리포지토리 추가
- 설정/발송로그에는 중복 처리와 스케줄러 갱신을 위한 pessimistic write 조회 메서드 추가
- 리포지토리 동작 검증을 위한 JPA 테스트 추가

## 테스트 방법
- `TaskDueEmailAlertRepositoryTest`에서 기존 사용자 기본 설정 백필, 활성 설정 조회, 수신자 정렬/중복 확인, 발송로그 조회/중복 차단을 검증한다.
- 이후 서비스 테스트에서 최대 수신자 5개 제한, 이메일 정규화, 발송 스킵/재시도 정책과 연결해 검증한다.

## 관련 테스트
- `apps/api/src/test/java/com/yegkim/task_reloader_api/alert/repository/TaskDueEmailAlertRepositoryTest.java`

## 한 줄 요약
- 작업 마감 이메일 알림의 설정/수신자/발송로그를 다루는 JPA 도메인, 리포지토리, 기본 저장소 테스트를 추가했다.
