package com.groovy.backend.domain.memoir;

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
 * Memoir(N:1) - User(N:1) 좋아요 중개 엔티티. 한 유저는 같은 회고록에 좋아요를 한 번만 남길 수 있다.
 */
@Entity
@Table(
	name = "memoir_likes",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_memoir_like",
		columnNames = {"memoir_id", "user_id"}
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoirLike extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "memoir_id", nullable = false)
	private Memoir memoir;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Builder
	public MemoirLike(Memoir memoir, User user) {
		this.memoir = memoir;
		this.user = user;
	}
}
