package com.groovy.backend.domain.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.user.dto.UserResponse;
import com.groovy.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	public ApiResponse<UserResponse> me(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "내 정보 조회에 성공했습니다.", userService.getMyInfo(email));
	}
}
