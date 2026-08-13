-- 회고록 좋아요: 한 유저가 같은 회고록에 한 번만 좋아요를 남길 수 있다.
CREATE TABLE `memoir_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `memoir_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memoir_like` (`memoir_id`,`user_id`),
  KEY `FK_memoir_likes_user` (`user_id`),
  CONSTRAINT `FK_memoir_likes_memoir` FOREIGN KEY (`memoir_id`) REFERENCES `memoirs` (`id`),
  CONSTRAINT `FK_memoir_likes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
