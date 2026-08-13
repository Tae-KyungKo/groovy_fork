package com.groovy.backend.domain.notification.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.domain.notification.Notification;
import com.groovy.backend.domain.notification.NotificationType;
import com.groovy.backend.domain.notification.dto.NotificationResponse;
import com.groovy.backend.domain.notification.repository.NotificationRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	public static final String REDIS_CHANNEL = "notifications";
	private static final String TICKET_KEY_PREFIX = "notification:subscribe-ticket:";
	private static final Duration TICKET_TTL = Duration.ofSeconds(30);
	private static final Long SSE_NO_TIMEOUT = 0L;

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	// 유저 한 명이 탭을 여러 개 열 수 있어 리스트로 관리한다. 이 맵은 이 서버 프로세스가
	// 직접 들고 있는 로컬 연결만 담으므로, 인스턴스가 여러 대여도 각자 자기 것만 관리하면 된다.
	private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

	// --- 알림 생성(NotificationEventListener 전용) ---

	@Transactional
	public void createAndPublish(Long recipientUserId, NotificationType type, String title, String message, Long targetStudyId) {
		User recipient = userRepository.findById(recipientUserId).orElse(null);
		if (recipient == null) {
			log.warn("존재하지 않는 알림 수신자: recipientUserId={}", recipientUserId);
			return;
		}

		Notification notification = notificationRepository.save(Notification.builder()
			.recipient(recipient)
			.type(type)
			.title(title)
			.message(message)
			.targetStudyId(targetStudyId)
			.build());

		publishToRedis(recipientUserId, NotificationResponse.from(notification));
	}

	private void publishToRedis(Long recipientUserId, NotificationResponse response) {
		try {
			String payload = objectMapper.writeValueAsString(new NotificationPushMessage(recipientUserId, response));
			redisTemplate.convertAndSend(REDIS_CHANNEL, payload);
		} catch (JsonProcessingException e) {
			log.error("알림 push 메시지 직렬화 실패: recipientUserId={}", recipientUserId, e);
		}
	}

	// --- 조회/읽음 처리 ---

	public List<NotificationResponse> listUnread(String email) {
		User user = getUser(email);
		return notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(user.getId()).stream()
			.map(NotificationResponse::from)
			.toList();
	}

	@Transactional
	public void markRead(String email, Long notificationId) {
		User user = getUser(email);
		notificationRepository.findByIdAndRecipientId(notificationId, user.getId())
			.ifPresent(Notification::markRead);
	}

	@Transactional
	public void markAllRead(String email) {
		User user = getUser(email);
		notificationRepository.findByRecipientIdAndReadFalse(user.getId())
			.forEach(Notification::markRead);
	}

	// --- SSE 구독 ---

	// EventSource는 커스텀 헤더를 못 보내므로, 짧게 사는 1회용 티켓으로 신원을 확인한다.
	public String issueTicket(String email) {
		User user = getUser(email);
		String ticket = UUID.randomUUID().toString();
		redisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket, String.valueOf(user.getId()), TICKET_TTL);
		return ticket;
	}

	// 1회용이므로 조회 즉시 지운다. 실패(만료/이미 사용됨)하면 null.
	public Long consumeTicket(String ticket) {
		String key = TICKET_KEY_PREFIX + ticket;
		String userId = redisTemplate.opsForValue().get(key);
		if (userId == null) {
			return null;
		}
		redisTemplate.delete(key);
		return Long.valueOf(userId);
	}

	public SseEmitter subscribe(Long userId) {
		SseEmitter emitter = new SseEmitter(SSE_NO_TIMEOUT);
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
		emitters.add(emitter);

		emitter.onCompletion(() -> removeEmitter(userId, emitter));
		emitter.onTimeout(() -> removeEmitter(userId, emitter));
		emitter.onError(e -> removeEmitter(userId, emitter));

		try {
			emitter.send(SseEmitter.event().name("connected").data("ok"));
		} catch (IOException e) {
			removeEmitter(userId, emitter);
		}

		return emitter;
	}

	private void removeEmitter(Long userId, SseEmitter emitter) {
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.get(userId);
		if (emitters == null) {
			return;
		}
		emitters.remove(emitter);
		if (emitters.isEmpty()) {
			emittersByUserId.remove(userId);
		}
	}

	// Redis 구독자(NotificationRedisSubscriber)가 채널 메시지를 받으면 호출한다.
	// 이 인스턴스가 그 유저의 연결을 안 들고 있으면(=수신자가 다른 인스턴스에 붙어 있으면) 아무 일도 안 한다.
	public void pushToLocalEmitters(NotificationPushMessage message) {
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.get(message.recipientUserId());
		if (emitters == null || emitters.isEmpty()) {
			return;
		}

		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name("notification").data(message.notification()));
			} catch (IOException e) {
				removeEmitter(message.recipientUserId(), emitter);
			}
		}
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 유저: email={}", email);
				return new IllegalArgumentException("존재하지 않는 유저입니다.");
			});
	}
}
