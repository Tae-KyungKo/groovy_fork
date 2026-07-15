package com.groovy.backend.domain.tag;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.domain.study.Study;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Study(N:1) - Tag(N:1) 매핑 중개 엔티티. {@code @ManyToMany} 대신 명시적으로 관계를 풀어
 * 스터디의 성향 태그를 독립된 로우로 관리한다.
 */
@Entity
@Table(
	name = "study_tags",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_study_tag",
		columnNames = {"study_id", "tag_id"}
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyTag extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id", nullable = false)
	private Study study;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tag_id", nullable = false)
	private Tag tag;

	@Builder
	public StudyTag(Study study, Tag tag) {
		this.study = study;
		this.tag = tag;
	}
}
