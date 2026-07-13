package com.groovy.backend.domain.user.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.user.dto.LoginRequest;
import com.groovy.backend.domain.user.dto.LoginResponse;
import com.groovy.backend.domain.user.dto.SignupRequest;
import com.groovy.backend.domain.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final UserService userService;

	@PostMapping("/signup")
	public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
		userService.signup(request);
		return ApiResponse.of("SUCCESS", "회원가입이 완료되었습니다.");
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = userService.login(request);
		return ApiResponse.of("SUCCESS", "로그인에 성공했습니다.", response);
	}
}
