package com.groovy.backend.domain.notification.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.notification.dto.NotificationResponse;
import com.groovy.backend.domain.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	public ApiResponse<List<NotificationResponse>> getNotifications(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "알림 목록 조회에 성공했습니다.", notificationService.listUnread(email));
	}

	@PostMapping("/{id}/read")
	public ApiResponse<Void> markRead(@AuthenticationPrincipal String email, @PathVariable Long id) {
		notificationService.markRead(email, id);
		return ApiResponse.of("SUCCESS", "알림을 읽음 처리했습니다.");
	}

	@PostMapping("/read-all")
	public ApiResponse<Void> markAllRead(@AuthenticationPrincipal String email) {
		notificationService.markAllRead(email);
		return ApiResponse.of("SUCCESS", "모든 알림을 읽음 처리했습니다.");
	}

	// EventSource 연결 직전에 호출하는 일반 인증 엔드포인트. 여기서 발급한 티켓으로 /subscribe에 붙는다.
	@PostMapping("/subscribe-ticket")
	public ApiResponse<Map<String, String>> issueSubscribeTicket(@AuthenticationPrincipal String email) {
		String ticket = notificationService.issueTicket(email);
		return ApiResponse.of("SUCCESS", "구독 티켓이 발급되었습니다.", Map.of("ticket", ticket));
	}

	// 브라우저 EventSource는 커스텀 헤더를 못 보내므로 이 경로는 SecurityConfig에서 permitAll이고,
	// 신원은 쿼리파라미터 ticket으로만 확인한다(@AuthenticationPrincipal 안 씀 — 인증 필터를 안 타는 경로라 항상 비어있음).
	@GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe(@RequestParam String ticket) {
		Long userId = notificationService.consumeTicket(ticket);
		if (userId == null) {
			throw new IllegalArgumentException("유효하지 않거나 만료된 구독 티켓입니다.");
		}
		return notificationService.subscribe(userId);
	}
}
