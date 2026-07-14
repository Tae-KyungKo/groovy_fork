package com.groovy.backend.domain.study.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.domain.study.dto.StudyCreateRequest;
import com.groovy.backend.domain.study.dto.StudyMatchResponse;
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

	// 리터럴 경로("/match")가 "/{studyId}" 변수 경로보다 우선 매칭되므로 순서와 무관하게 안전하다.
	// tagIds가 주어지면 즉석으로 선택한 태그 기준으로, 없으면 로그인 유저의 저장된 선호 태그 기준으로 매칭한다.
	@GetMapping("/match")
	public ApiResponse<List<StudyMatchResponse>> getMatchedStudies(
		@AuthenticationPrincipal String email,
		@RequestParam(required = false) List<Long> tagIds
	) {
		return ApiResponse.of("SUCCESS", "태그 매칭 스터디 조회에 성공했습니다.", studyService.getMatchedStudies(email, tagIds));
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
