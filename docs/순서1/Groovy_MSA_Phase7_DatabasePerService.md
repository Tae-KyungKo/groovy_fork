# Groovy MSA 전환 — Phase 7: Database per Service (논리적 분리부터)

> 상위 계획: [`Groovy_MSA_전환계획.md`](Groovy_MSA_전환계획.md) Phase 7
> 선행 문서: [`Groovy_MSA_Phase6_NotificationExtraction.md`](Groovy_MSA_Phase6_NotificationExtraction.md)
> 목표: 하나의 MySQL 컨테이너 안에서 서비스별 스키마 소유권부터 분리한다(인스턴스를 여러 개
> 띄우지 않는다).

## 1. 범위를 좁힌 이유 — "5개 스키마"가 아니라 "1개"부터

계획서의 목표 그림은 `identity_db/study_db/content_db/calendar_db/notification_db` 5개지만,
지금 실제로 별도 프로세스로 추출된 서비스는 **notification-service 하나뿐**이다. User/Study/
Memoir/Calendar/Tag는 여전히 legacy-monolith라는 하나의 JVM 안에서 서로 실시간 JPA
연관관계(`Memoir.study`, `Memoir.author`, `Calendar.user`, `Study.leader`, `Application.study/
applicant` 등 — Calendar만 Phase 1에서 예외적으로 ID 참조로 바꿔뒀다)로 묶여 있다.

지금 이 4개 도메인의 테이블을 스키마 4개로 물리적으로 쪼개려면:
1. 그 전에 남아있는 모든 cross-domain JPA 연관관계를 Calendar처럼 ID 참조로 먼저 바꿔야
   한다(그렇지 않으면 Hibernate가 다른 스키마의 테이블을 SQL JOIN하려다 실패한다) — 이건
   Phase 1 범위를 몇 배로 늘리는 별도의 큰 작업이다.
2. 그렇게 바꾼다 해도 legacy-monolith는 여전히 **하나의 애플리케이션**이 그 4개 스키마
   전부에 접근해야 하므로(User/Study/Memoir/Calendar가 다 같은 코드베이스), "권한을 자기
   스키마로 제한"이라는 이번 Phase의 핵심 완료 기준 자체를 만족시킬 서비스 경계가 없다.

즉 서비스 경계가 없는 상태에서 스키마만 먼저 쪼개는 건 이 Phase의 목적(권한 경계 강제)에
전혀 기여하지 못하는 순수 비용이다. 그래서 **실제로 서비스 경계가 존재하는 notification-service만
분리하고, 나머지 4개는 각 도메인이 실제로 추출되는 시점에 "서비스 추출 + DB 분리"를 함께
진행한다**(Phase 6에서 Notification에 했던 것과 같은 패턴). 이 판단 자체가 Phase 0 원칙
3번("DB 분리는 마지막이 아니라 중간 지점")과 배치되지 않는다 — 오히려 "서비스가 있는 곳부터
중간 지점을 통과시킨다"는 같은 원칙의 적용이다.

## 2. 실제로 한 일

### 2-1. notification_db 스키마 + 전용 계정

`mysql-init/01-notification-service.sql`(신규, `msa-mysql`의 `docker-entrypoint-initdb.d`에
마운트되어 컨테이너 최초 기동 시 자동 실행):

```sql
CREATE DATABASE IF NOT EXISTS notification_db ...;
CREATE USER IF NOT EXISTS 'notification_service'@'%' IDENTIFIED BY '...';
GRANT ALL PRIVILEGES ON notification_db.* TO 'notification_service'@'%';
```

`notification_service` 계정은 `notification_db` 외에는 **아무 권한도 없다** — `groovy_db`는
물론 다른 어떤 스키마도 못 본다. (로컬 검증 전용 휘발성 DB라 비밀번호를 파일에 고정값으로
뒀다 — 실제 배포라면 시크릿 매니저로 주입해야 한다. Phase 10/11에서 다룰 영역.)

### 2-2. notification-service가 자기 스키마를 직접 소유

Phase 6에서는 legacy의 Flyway 이력(V6/V8)에 얹혀가며 별도 마이그레이션 없이 `groovy_db`를
공유했다. 이번에 진짜 소유권을 옮겼다:

- `backend/services/notification-service/src/main/resources/db/migration/
  V1__create_notifications_table.sql` 신규 — legacy의 V6+V8과 컬럼/인덱스는 동일하지만
  **`recipient_id`의 FK 제약(`REFERENCES users(id)`)은 넣지 않았다** — users 테이블이 물리적으로
  다른 스키마에 있어 애초에 만들 수도 없고, 계획서 원칙("서비스 경계를 넘는 참조는 DB FK가
  아니라 Service-level reference")과 정확히 일치한다.
- `build.gradle`에 Flyway 의존성 재추가(`spring-boot-flyway`, `flyway-core`, `flyway-mysql`).
- `application.yml`의 기본 datasource가 `notification_db` + `notification_service` 계정을
  가리키도록 변경.

### 2-3. legacy에서 옛 테이블 정리

`groovy/src/main/resources/db/migration/V9__drop_notifications_table.sql` 신규 —
`DROP TABLE IF EXISTS notifications;`. Phase 6에서 이미 legacy 코드는 이 테이블을 전혀
참조하지 않았으므로(엔티티/리포지토리 삭제됨), 물리적 데이터 소유권도 이번에 완전히
넘겼다. (연습 프로젝트라 데이터 백필 없이 드롭 — 실제 운영이었다면 `notification_db`로
백필 후 드롭하는 별도 절차가 필요하다고 문서에 명시.)

### 2-4. docker-compose.msa.yml

- `msa-mysql`에 `mysql-init/`을 `docker-entrypoint-initdb.d`로 마운트.
- `notification-service` 환경변수를 `groovy_db`/`root` → `notification_db`/
  `notification_service`로 변경.

## 3. 검증 결과 (실제 DB 쿼리로 확인)

```sql
-- groovy_db에서 notifications 테이블이 사라짐
SHOW TABLES FROM groovy_db LIKE 'notifications';   -- 결과 없음

-- notification_db가 자기 Flyway 이력을 갖고 테이블을 소유
SHOW TABLES FROM notification_db;
-- flyway_schema_history
-- notifications

-- notification_service 계정: 자기 스키마는 접근 가능
SELECT COUNT(*) FROM notification_db.notifications;   -- 성공, cnt=0

-- notification_service 계정: groovy_db는 완전히 차단 (완료 기준의 "권한 에러" 실측)
SELECT * FROM groovy_db.studies;
-- ERROR 1142 (42000): SELECT command denied to user 'notification_service'@'localhost' for table 'studies'

USE groovy_db;
-- ERROR 1044 (42000): Access denied for user 'notification_service'@'%' to database 'groovy_db'
```

**기능 회귀 없음**: 새 유저로 회원가입 → 로그인 → 스터디 생성 → 참여 신청 → (legacy →
NotificationEventBridge → HTTP → notification-service) → `notification_db`에 저장된 알림을
API Gateway 경유로 정상 조회. Phase 6와 동일한 end-to-end 시나리오가 완전히 분리된 스키마
위에서도 그대로 동작함을 확인.

## 4. 완료 기준 체크

- [x] 서비스별 DB 계정 생성, 권한이 자기 스키마로 제한됨 — `notification_service` 계정 실측
- [x] 코드에서 cross-schema JOIN이 물리적으로 실행 불가능함을 확인 (권한 에러 발생 테스트) —
      `ERROR 1142`/`ERROR 1044` 실측
- [x] FK 제약 제거 및 애플리케이션 레벨 검증으로 대체 — 새 스키마는 애초에 FK 없이 생성,
      애플리케이션(notification-service)이 recipientId를 그대로 신뢰

나머지 4개 도메인(identity_db/study_db/content_db/calendar_db)은 §1에서 설명한 대로 **의도적으로
보류** — 각 도메인이 실제로 서비스로 추출되는 시점에 함께 진행한다.

## 5. 다음 단계로 넘길 것

- **다음 서비스 추출 시 이번 Phase 패턴을 재사용**: (1) 그 도메인의 남은 cross-domain JPA
  연관관계를 ID 참조로 전환(Phase 1 Calendar 패턴) → (2) 별도 코드베이스로 이동(Phase 6
  Notification 패턴) → (3) 전용 스키마 + 계정 분리(이번 Phase 패턴) → (4) legacy에서 옛
  테이블 드롭. 이 4단계가 앞으로 User/Study/Memoir/Calendar를 뽑아낼 때 반복될 표준 절차다.
- **Phase 8(동기 통신)**: notification-service가 이미 `/internal/notifications`로 동기 REST를
  쓰고 있으니, 다음 서비스 추출 시에도 이 패턴(RestClient + Internal API)을 그대로 재사용한다.
- **비밀번호 하드코딩**: `mysql-init/01-notification-service.sql`과 compose의 기본값이 평문
  고정값이다 — Phase 10/11에서 시크릿 관리로 교체.
