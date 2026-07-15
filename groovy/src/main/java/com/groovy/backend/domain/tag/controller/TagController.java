package com.groovy.backend.domain.tag.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.tag.dto.TagResponse;
import com.groovy.backend.domain.tag.dto.UserTagUpdateRequest;
import com.groovy.backend.domain.tag.service.TagService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

	private final TagService tagService;

	@GetMapping
	public ApiResponse<List<TagResponse>> getTags() {
		return ApiResponse.of("SUCCESS", "전체 태그 목록 조회에 성공했습니다.", tagService.getTags());
	}

	@GetMapping("/me")
	public ApiResponse<List<TagResponse>> getMyTags(@AuthenticationPrincipal String email) {
		return ApiResponse.of("SUCCESS", "내 선호 태그 조회에 성공했습니다.", tagService.getUserTags(email));
	}

	@PutMapping("/me")
	public ApiResponse<Void> updateMyTags(
		@AuthenticationPrincipal String email,
		@Valid @RequestBody UserTagUpdateRequest request
	) {
		tagService.updateUserTags(email, request.tagIds());
		return ApiResponse.of("SUCCESS", "선호 태그가 저장되었습니다.");
	}
}
