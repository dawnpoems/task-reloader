# 작업 단위: p1-task-due-email-alert-mail-sender

## 문제
- 작업 마감 이메일 알림 집계 결과를 실제 이메일 본문으로 만들고 SMTP로 발송하는 서비스가 필요하다.
- 이후 스케줄러는 집계 결과와 수신자 목록만 넘겨 메일을 보낼 수 있어야 한다.

## 결정
- Spring Mail의 `JavaMailSender`를 사용해 실제 SMTP 발송을 구현한다.
- 템플릿은 별도 템플릿 엔진 없이 Java 렌더러에서 HTML/plain text를 함께 생성한다.
- 수신자별 개별 발송으로 구현해 이후 실패 로그/재시도 추적을 단순하게 만든다.

## 트레이드오프
- 장점: SMTP 설정만 채우면 바로 실제 메일 발송이 가능하고, 스케줄러 단계에서 재사용하기 쉽다.
- 단점: SMTP 제공자 설정이 필요하며, 로컬 개발에서는 Mailpit/Mailhog 같은 캡처 도구를 별도로 띄워야 한다.

## 구현 요약
- `spring-boot-starter-mail` 의존성 추가
- SMTP 및 작업 마감 이메일 알림 발신 설정 추가
- `TaskDueEmailAlertMailTemplateRenderer` 추가
- `TaskDueEmailAlertMailSender` 추가
- 메일 발송 실패 전용 예외 추가
- `infra/.env.example`, `infra/README.md`에 메일 설정 키 문서화

## 테스트 방법
- 컴파일로 Spring Mail 의존성과 신규 렌더러/발송 서비스가 빌드 가능한지 확인한다.
- 이후 테스트 코드에서 0건/수신자 없음 스킵, 제목/HTML/plain text 생성, JavaMailSender 호출/예외 전파를 검증한다.

## 관련 테스트
- 자동화 테스트 추가 없음 (이번 단계는 구현 코드만 진행)

## 한 줄 요약
- 작업 마감 이메일 알림 집계 결과를 HTML/plain text 메일로 렌더링하고 SMTP로 발송하는 서비스를 추가했다.
