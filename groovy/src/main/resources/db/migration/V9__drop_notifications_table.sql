-- MSA 전환 Phase 7(DB per Service): Notification 도메인은 Phase 6에서 이미 코드가
-- notification-service로 옮겨갔고(레거시에는 관련 엔티티/리포지토리가 전혀 없음),
-- 이 스키마의 물리적 데이터/테이블 소유권도 notification_db로 옮긴다.
-- 이 테이블은 이제 어떤 레거시 코드에서도 참조되지 않으므로 정리한다.
-- (연습 프로젝트라 데이터 백필 없이 드롭한다 — 실제 운영이었다면 notification_db로
-- 데이터를 옮긴 뒤 드롭하는 별도 백필 절차가 필요하다.)
DROP TABLE IF EXISTS notifications;
