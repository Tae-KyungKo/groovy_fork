package com.groovy.backend.domain.memoir.dto;

import java.time.LocalDateTime;

import com.groovy.backend.domain.memoir.MemoirComment;

public record MemoirCommentResponse(
	String id,
	String memoirId,
	String authorId,
	String authorName,
	String content,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static MemoirCommentResponse from(MemoirComment comment) {
		return new MemoirCommentResponse(
			String.valueOf(comment.getId()),
			String.valueOf(comment.getMemoir().getId()),
			String.valueOf(comment.getAuthor().getId()),
			comment.getAuthor().getName(),
			comment.getContent(),
			comment.getCreatedAt(),
			comment.getUpdatedAt()
		);
	}
}
