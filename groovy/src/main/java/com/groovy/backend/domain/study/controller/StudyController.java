package com.groovy.backend.domain.study.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.study.dto.StudyCreateRequest;
import com.groovy.backend.domain.study.dto.StudyResponse;
import com.groovy.backend.domain.study.dto.StudyUpdateRequest;
import com.groovy.backend.domain.study.service.StudyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
public class StudyController {

	private final StudyService studyService;

	@PostMapping
	public ApiResponse<Long> createStudy(
		@AuthenticationPrincipal String email,
		@Valid @RequestBody StudyCreateRequest request
	) {
		return ApiResponse.of("SUCCESS", "스터디 그룹이 생성되었습니다.", studyService.createStudy(email, request));
	}

	@GetMapping
	public ApiResponse<Page<StudyResponse>> getStudies(
		@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
	) {
		return ApiResponse.of("SUCCESS", "스터디 목록 조회에 성공했습니다.", studyService.getStudies(pageable));
	}

	@GetMapping("/{studyId}")
	public ApiResponse<StudyResponse> getStudy(@PathVariable Long studyId) {
		return ApiResponse.of("SUCCESS", "스터디 상세 조회에 성공했습니다.", studyService.getStudy(studyId));
	}

	@PutMapping("/{studyId}")
	public ApiResponse<Void> updateStudy(
		@AuthenticationPrincipal String email,
		@PathVariable Long studyId,
		@Valid @RequestBody StudyUpdateRequest request
	) {
		studyService.updateStudy(email, studyId, request);
		return ApiResponse.of("SUCCESS", "스터디 정보가 수정되었습니다.");
	}

	@DeleteMapping("/{studyId}")
	public ApiResponse<Void> deleteStudy(@AuthenticationPrincipal String email, @PathVariable Long studyId) {
		studyService.deleteStudy(email, studyId);
		return ApiResponse.of("SUCCESS", "스터디가 삭제되었습니다.");
	}
}
