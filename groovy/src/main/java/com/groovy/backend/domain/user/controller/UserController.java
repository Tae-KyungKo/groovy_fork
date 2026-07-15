package com.groovy.backend.domain.user.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.study.dto.MyApplicationResponse;
import com.groovy.backend.domain.study.dto.StudyResponse;
import com.groovy.backend.domain.study.service.ApplicationService;
import com.groovy.backend.domain.study.service.StudyService;
import com.groovy.backend.domain.user.dto.UserResponse;
import com.groovy.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final StudyService studyService;
	private final ApplicationService applicationService;

	@GetMapping("/me")
	public ApiResponse<UserResponse> me(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "내 정보 조회에 성공했습니다.", userService.getMyInfo(email));
	}

	// 마이페이지 "내가 만든 스터디" 목록.
	@GetMapping("/me/studies")
	public ApiResponse<List<StudyResponse>> getMyStudies(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "내가 만든 스터디 조회에 성공했습니다.", studyService.getMyStudies(email));
	}

	// 마이페이지 "참여 중인 스터디 / 신청 내역" 목록.
	@GetMapping("/me/applications")
	public ApiResponse<List<MyApplicationResponse>> getMyApplications(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "내 신청 내역 조회에 성공했습니다.", applicationService.getMyApplications(email));
	}
}
