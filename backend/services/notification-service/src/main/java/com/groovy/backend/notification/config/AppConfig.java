package com.groovy.backend.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fasterxml.jackson.databind.ObjectMapper;

// @EnableScheduling: 알림 정리 배치(NotificationCleanupScheduler)의 @Scheduled가 동작하려면 필요.
// @EnableKafka: Phase 9 — NotificationEventConsumer의 @KafkaListener가 동작하려면 필요.
@Configuration
@EnableScheduling
@EnableKafka
public class AppConfig {

	// JacksonAutoConfiguration이 자동으로 만들어주는 건 Boot 4의 네이티브 tools.jackson
	// ObjectMapper라, Redis pub/sub 직렬화(구 Jackson 2, com.fasterxml.jackson)에 쓸 빈은
	// 여기서 직접 등록한다(legacy의 AsyncConfig와 동일한 이유).
	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
