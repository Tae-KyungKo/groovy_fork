package com.groovy.backend.domain.notification;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipient_id", nullable = false)
	private User recipient;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, length = 500)
	private String message;

	// 클릭 시 이동할 대상(스터디/회고록 등)의 id. 타입에 따라 대상 페이지가 다르므로
	// 프론트가 type과 함께 조합해서 라우팅한다.
	@Column(name = "target_id")
	private Long targetId;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Builder
	public Notification(User recipient, NotificationType type, String title, String message, Long targetId) {
		this.recipient = recipient;
		this.type = type;
		this.title = title;
		this.message = message;
		this.targetId = targetId;
		this.read = false;
	}

	public void markRead() {
		this.read = true;
	}
}
