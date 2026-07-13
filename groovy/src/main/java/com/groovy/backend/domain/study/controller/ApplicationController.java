package com.groovy.backend.domain.study.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.study.dto.ApplicationResponse;
import com.groovy.backend.domain.study.dto.ApplicationStatusUpdateRequest;
import com.groovy.backend.domain.study.service.ApplicationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/studies/{studyId}/applications")
@RequiredArgsConstructor
public class ApplicationController {

	private final ApplicationService applicationService;

	@PostMapping
	public ApiResponse<Long> apply(@AuthenticationPrincipal String email, @PathVariable Long studyId) {
		return ApiResponse.of("SUCCESS", "참여 신청이 완료되었습니다.", applicationService.apply(email, studyId));
	}

	@DeleteMapping
	public ApiResponse<Void> cancel(@AuthenticationPrincipal String email, @PathVariable Long studyId) {
		applicationService.cancel(email, studyId);
		return ApiResponse.of("SUCCESS", "참여 신청이 취소되었습니다.");
	}

	@GetMapping
	public ApiResponse<List<ApplicationResponse>> getApplications(
		@AuthenticationPrincipal String email,
		@PathVariable Long studyId
	) {
		return ApiResponse.of("SUCCESS", "신청 목록 조회에 성공했습니다.", applicationService.getApplications(email, studyId));
	}

	@PatchMapping("/{applicationId}")
	public ApiResponse<Void> updateStatus(
		@AuthenticationPrincipal String email,
		@PathVariable Long studyId,
		@PathVariable Long applicationId,
		@Valid @RequestBody ApplicationStatusUpdateRequest request
	) {
		applicationService.updateStatus(email, studyId, applicationId, request.status());
		return ApiResponse.of("SUCCESS", "신청 상태가 변경되었습니다.");
	}
}
