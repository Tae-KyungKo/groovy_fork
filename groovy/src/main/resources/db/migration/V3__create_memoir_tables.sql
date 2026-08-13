-- 회고록 게시판: 스터디(N:1)와 작성자(N:1)에 연결된 회고록과, 회고록에 달리는 댓글.
CREATE TABLE `memoirs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `study_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_memoirs_study` (`study_id`),
  KEY `FK_memoirs_author` (`author_id`),
  CONSTRAINT `FK_memoirs_study` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`),
  CONSTRAINT `FK_memoirs_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `memoir_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` text NOT NULL,
  `memoir_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_memoir_comments_memoir` (`memoir_id`),
  KEY `FK_memoir_comments_author` (`author_id`),
  CONSTRAINT `FK_memoir_comments_memoir` FOREIGN KEY (`memoir_id`) REFERENCES `memoirs` (`id`),
  CONSTRAINT `FK_memoir_comments_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 회고록/댓글 작성 시 누적되는 스터디 팀의 레벨/경험치. 별도 테이블 대신 studies에 컬럼으로 둔다
-- (스터디당 현재 값 1개만 필요하고 이력 조회 요구사항이 없어 Study 엔티티 필드로 충분하다).
ALTER TABLE studies
  ADD COLUMN level INT NOT NULL DEFAULT 1,
  ADD COLUMN exp_point INT NOT NULL DEFAULT 0;
