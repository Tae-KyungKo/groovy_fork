CREATE TABLE `study_waitlists` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `study_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_waitlist_study_user` (`study_id`, `user_id`),
  KEY `FK_study_waitlists_user` (`user_id`),
  CONSTRAINT `FK_study_waitlists_study` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`),
  CONSTRAINT `FK_study_waitlists_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
