package com.groovy.backend.domain.calendar.dto;

import com.groovy.backend.domain.study.Study;

/**
 * 캘린더에서 "스터디 약속" 등록 시 내가 속한 스터디를 고르기 위한 최소 정보.
 */
public record MyStudyOptionResponse(
	String studyId,
	String title
) {

	public static MyStudyOptionResponse from(Study study) {
		return new MyStudyOptionResponse(String.valueOf(study.getId()), study.getTitle());
	}
}
