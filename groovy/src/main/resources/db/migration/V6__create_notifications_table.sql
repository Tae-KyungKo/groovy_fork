CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `recipient_id` bigint NOT NULL,
  `type` varchar(30) NOT NULL,
  `title` varchar(255) NOT NULL,
  `message` varchar(500) NOT NULL,
  -- 클릭 시 이동할 대상의 id. 스터디/회고록 등 타입마다 가리키는 리소스가 달라, type과 조합해서
  -- 프론트가 라우팅한다(한 알림당 하나의 대상만 있으므로 필드 하나로 충분).
  `target_id` bigint DEFAULT NULL,
  `is_read` boolean NOT NULL DEFAULT false,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_recipient_unread` (`recipient_id`, `is_read`),
  CONSTRAINT `FK_notifications_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
