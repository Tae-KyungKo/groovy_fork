package com.groovy.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MSA 전환 Phase 9: Outbox Relay(OutboxRelay)의 @Scheduled 폴링이 동작하려면
 * @EnableScheduling이 필요하다(예전 AsyncConfig의 @EnableAsync/notificationTaskExecutor는
 * HTTP 브릿지가 Kafka Outbox로 대체되며 더 이상 필요 없어져 제거했다).
 *
 * ObjectMapper 빈: JacksonAutoConfiguration이 자동으로 만들어주는 건 Boot 4의 네이티브
 * tools.jackson ObjectMapper라, OutboxEventWriter가 쓰는 구 Jackson(com.fasterxml.jackson)
 * ObjectMapper는 여기서 직접 등록해야 한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
