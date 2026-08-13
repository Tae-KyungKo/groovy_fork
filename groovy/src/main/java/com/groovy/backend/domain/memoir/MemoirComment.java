package com.groovy.backend.domain.memoir;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "memoir_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoirComment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "memoir_id", nullable = false)
	private Memoir memoir;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Builder
	public MemoirComment(Memoir memoir, User author, String content) {
		this.memoir = memoir;
		this.author = author;
		this.content = content;
	}

	public void update(String content) {
		this.content = content;
	}

	public boolean isAuthor(Long userId) {
		return this.author.getId().equals(userId);
	}
}
