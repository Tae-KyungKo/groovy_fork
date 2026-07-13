package com.groovy.backend.domain.calendar.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.calendar.dto.CalendarCreateRequest;
import com.groovy.backend.domain.calendar.dto.IntegrationCalendarResponse;
import com.groovy.backend.domain.calendar.service.CalendarService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/calendars")
@RequiredArgsConstructor
public class CalendarController {

	private final CalendarService calendarService;

	@GetMapping
	public ApiResponse<List<IntegrationCalendarResponse>> getCalendars(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "통합 일정 조회에 성공했습니다.", calendarService.getIntegratedCalendar(email));
	}

	@PostMapping
	public ApiResponse<Long> addCalendar(
		@AuthenticationPrincipal String email,
		@Valid @RequestBody CalendarCreateRequest request
	) {
		return ApiResponse.of("SUCCESS", "개인 일정이 추가되었습니다.", calendarService.addSchedule(email, request));
	}
}
