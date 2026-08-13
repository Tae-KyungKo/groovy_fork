package com.groovy.backend.domain.study.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.study.service.WaitlistService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/studies/{studyId}/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

	private final WaitlistService waitlistService;

	@PostMapping
	public ApiResponse<Void> register(@AuthenticationPrincipal String email, @PathVariable Long studyId) {
		waitlistService.register(email, studyId);
		return ApiResponse.of("SUCCESS", "빈자리 알림을 등록했습니다.");
	}

	@DeleteMapping
	public ApiResponse<Void> cancel(@AuthenticationPrincipal String email, @PathVariable Long studyId) {
		waitlistService.cancel(email, studyId);
		return ApiResponse.of("SUCCESS", "빈자리 알림을 취소했습니다.");
	}
}
