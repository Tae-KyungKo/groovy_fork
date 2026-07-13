package com.groovy.backend.domain.study.dto;

public record StudyMatchResponse(StudyResponse study, int matchedTagCount, double matchScore) {

	public static StudyMatchResponse of(StudyResponse study, int matchedTagCount, double matchScore) {
		return new StudyMatchResponse(study, matchedTagCount, matchScore);
	}
}
