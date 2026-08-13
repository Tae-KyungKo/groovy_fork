package com.groovy.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.groovy.backend.domain.notification.service.NotificationRedisSubscriber;
import com.groovy.backend.domain.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * RedisConnectionFactory/StringRedisTemplate은 spring-boot-starter-data-redis가 붙어있으면
 * Spring Boot가 spring.data.redis.host/port 설정으로 자동 구성해준다. 여기서는 pub/sub
 * 구독을 위한 RedisMessageListenerContainer만 직접 등록한다.
 * ObjectMapper 빈은 AsyncConfig에 있다 — 여기 두면 NotificationRedisSubscriber(이 클래스의
 * 생성자 의존성)가 ObjectMapper를 필요로 해서 순환 참조가 생기기 때문에 의존성 없는 곳으로 분리했다.
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

	private final NotificationRedisSubscriber notificationRedisSubscriber;

	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(notificationRedisSubscriber, new ChannelTopic(NotificationService.REDIS_CHANNEL));
		return container;
	}
}
