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
import com.groovy.backend.domain.calendar.dto.CalendarEventResponse;
import com.groovy.backend.domain.calendar.dto.MyStudyOptionResponse;
import com.groovy.backend.domain.calendar.service.CalendarService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/calendars")
@RequiredArgsConstructor
public class CalendarController {

	private final CalendarService calendarService;

	@GetMapping
	public ApiResponse<List<CalendarEventResponse>> getCalendars(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "통합 일정 조회에 성공했습니다.", calendarService.getIntegratedCalendar(email));
	}

	// 캘린더에서 "스터디 약속" 등록 시 고를 수 있는, 내가 방장이거나 승인되어 속한 스터디 목록.
	@GetMapping("/studies")
	public ApiResponse<List<MyStudyOptionResponse>> getMyStudyOptions(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "내 스터디 목록 조회에 성공했습니다.", calendarService.getMyStudyOptions(email));
	}

	@PostMapping
	public ApiResponse<CalendarEventResponse> addCalendar(
		@AuthenticationPrincipal String email,
		@Valid @RequestBody CalendarCreateRequest request
	) {
		return ApiResponse.of("SUCCESS", "일정이 추가되었습니다.", calendarService.addSchedule(email, request));
	}
}
