-- MSA 전환 Phase 9: Transactional Outbox. 도메인 서비스가 비즈니스 데이터를 저장하는 트랜잭션
-- 안에서 함께 기록하는 "발행 예정 이벤트" 테이블. OutboxRelay가 이 테이블을 폴링해 Kafka로
-- 실제 발행하고 published=true로 표시한다.
CREATE TABLE `outbox_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` varchar(36) NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `payload` TEXT NOT NULL,
  `published` boolean NOT NULL DEFAULT false,
  `published_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_unpublished` (`published`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
