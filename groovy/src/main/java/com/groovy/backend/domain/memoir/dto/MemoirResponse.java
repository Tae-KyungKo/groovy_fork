package com.groovy.backend.domain.memoir.dto;

import java.time.LocalDateTime;

import com.groovy.backend.domain.memoir.Memoir;

public record MemoirResponse(
	String id,
	String studyId,
	String studyTitle,
	String authorId,
	String authorName,
	String title,
	String content,
	long commentCount,
	long likeCount,
	boolean liked,
	Integer studyLevel,
	Integer studyExpPoint,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static MemoirResponse from(Memoir memoir, long commentCount, long likeCount, boolean liked) {
		return new MemoirResponse(
			String.valueOf(memoir.getId()),
			String.valueOf(memoir.getStudy().getId()),
			memoir.getStudy().getTitle(),
			String.valueOf(memoir.getAuthor().getId()),
			memoir.getAuthor().getName(),
			memoir.getTitle(),
			memoir.getContent(),
			commentCount,
			likeCount,
			liked,
			memoir.getStudy().getLevel(),
			memoir.getStudy().getExpPoint(),
			memoir.getCreatedAt(),
			memoir.getUpdatedAt()
		);
	}
}
