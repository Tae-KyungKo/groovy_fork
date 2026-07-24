package com.groovy.backend.domain.tag;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.domain.user.User;

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
 * User(N:1) - Tag(N:1) 매핑 중개 엔티티. {@code @ManyToMany} 대신 명시적으로 관계를 풀어
 * 유저의 선호 태그를 독립된 로우로 관리한다.
 */
@Entity
@Table(
	name = "user_tags",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_user_tag",
		columnNames = {"user_id", "tag_id"}
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTag extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tag_id", nullable = false)
	private Tag tag;

	@Builder
	public UserTag(User user, Tag tag) {
		this.user = user;
		this.tag = tag;
	}
}
