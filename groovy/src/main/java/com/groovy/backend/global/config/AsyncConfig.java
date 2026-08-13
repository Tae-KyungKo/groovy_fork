package com.groovy.backend.global.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableAsync
public class AsyncConfig {

	// 알림 생성 + SSE push를 요청 스레드와 분리해서, 승인/신청/일정변경 같은 API 응답이
	// 알림 전송(DB insert + Redis publish) 때문에 느려지지 않게 한다.
	@Bean(name = "notificationTaskExecutor")
	public Executor notificationTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("notify-");
		executor.initialize();
		return executor;
	}

	// 이 프로젝트는 spring-boot-starter-web이 아니라 더 좁은 spring-boot-starter-webmvc만 쓰고
	// 있어서 JacksonAutoConfiguration이 ObjectMapper 빈을 자동으로 만들어주지 않는다(HTTP 메시지
	// 컨버터 자체는 내부적으로 별도 처리되어 계속 잘 동작하지만, 그 ObjectMapper는 빈으로 노출되지
	// 않는다). 알림 도메인이 Redis pub/sub 직렬화에 직접 필요해서 여기서 등록한다.
	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
