CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) DEFAULT NULL,
  `provider_type` enum('GOOGLE','KAKAO','LOCAL') NOT NULL,
  `role_type` enum('ADMIN','USER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` enum('OPERATING_POLICY','STUDY_MODE') NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt48xdq560gs3gap9g7jg36kgc` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `studies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `capacity` int NOT NULL,
  `description` text NOT NULL,
  `meeting_end_time` time DEFAULT NULL,
  `meeting_start_time` time DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `leader_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKacx7wxxsm81ks66nckkefdf7i` (`leader_id`),
  CONSTRAINT `FKacx7wxxsm81ks66nckkefdf7i` FOREIGN KEY (`leader_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- end_date는 이후 마이그레이션에서 추가되므로 베이스라인에는 포함하지 않는다.
CREATE TABLE `calendars` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `date` date NOT NULL,
  `title` varchar(255) NOT NULL,
  `study_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4g8bqpbm4dovnwf8jvs85iqeg` (`study_id`),
  KEY `FK2ef443fpfyuaay9nc09tvhjre` (`user_id`),
  CONSTRAINT `FK2ef443fpfyuaay9nc09tvhjre` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK4g8bqpbm4dovnwf8jvs85iqeg` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `applicant_id` bigint NOT NULL,
  `study_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_application_study_applicant` (`study_id`,`applicant_id`),
  KEY `FKgi56fay19nbaiamp5xyhd7ptc` (`applicant_id`),
  CONSTRAINT `FK3mpjyay97ibho7hhi5dhbmfl2` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`),
  CONSTRAINT `FKgi56fay19nbaiamp5xyhd7ptc` FOREIGN KEY (`applicant_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `study_meeting_days` (
  `study_id` bigint NOT NULL,
  `day_of_week` enum('FRI','MON','SAT','SUN','THU','TUE','WED') NOT NULL,
  PRIMARY KEY (`study_id`,`day_of_week`),
  CONSTRAINT `FK6d5017mnvp7n4km9rv7wm3gdf` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `study_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `study_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_tag` (`study_id`,`tag_id`),
  KEY `FKb3tn157xvu7imq0p1s2q9u6xn` (`tag_id`),
  CONSTRAINT `FKb3tn157xvu7imq0p1s2q9u6xn` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`),
  CONSTRAINT `FKmg1qhtb2vfg6va3sykiukrikj` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `tag_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tag` (`user_id`,`tag_id`),
  KEY `FKioatd2q4dvvsb5k6al6ge8au4` (`tag_id`),
  CONSTRAINT `FKdylhtw3qjb2nj40xp50b0p495` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKioatd2q4dvvsb5k6al6ge8au4` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
