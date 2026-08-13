package com.groovy.backend.domain.memoir;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.domain.study.Study;
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
@Table(name = "memoirs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memoir extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id", nullable = false)
	private Study study;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	@Column(nullable = false)
	private String title;

	// 마크다운 원문을 그대로 저장하고, 렌더링은 프론트에서 처리한다.
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Builder
	public Memoir(Study study, User author, String title, String content) {
		this.study = study;
		this.author = author;
		this.title = title;
		this.content = content;
	}

	public void update(String title, String content) {
		this.title = title;
		this.content = content;
	}

	public boolean isAuthor(Long userId) {
		return this.author.getId().equals(userId);
	}
}
