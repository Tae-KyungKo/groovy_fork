package com.groovy.backend.domain.study.dto;

import java.time.LocalDateTime;
import java.util.List;

// 프론트엔드는 StudyMatch를 "Study 필드 + matchScore"의 평탄한 구조로 소비하므로
// StudyResponse를 중첩시키지 않고 필드를 그대로 펼쳐서 내려준다.
public record StudyMatchResponse(
	Long id,
	String title,
	String description,
	Long ownerId,
	String ownerName,
	Integer capacity,
	Long memberCount,
	List<Long> tagIds,
	LocalDateTime createdAt,
	double matchScore
) {

	public static StudyMatchResponse of(StudyResponse study, double matchScore) {
		return new StudyMatchResponse(
			study.id(),
			study.title(),
			study.description(),
			study.ownerId(),
			study.ownerName(),
			study.capacity(),
			study.memberCount(),
			study.tagIds(),
			study.createdAt(),
			matchScore
		);
	}
}
