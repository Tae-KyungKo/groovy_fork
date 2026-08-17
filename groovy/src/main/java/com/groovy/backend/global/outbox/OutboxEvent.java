package com.groovy.backend.global.outbox;

import java.time.LocalDateTime;

import com.groovy.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MSA 전환 Phase 9: Transactional Outbox. 도메인 서비스가 비즈니스 데이터를 저장하는 것과
 * "이 일이 일어났다"는 이벤트 기록을 같은 트랜잭션 안에서 함께 남긴다(OutboxEventWriter 참고).
 * 실제 브로커 발행은 별도 프로세스(OutboxRelay)가 이 테이블을 폴링하며 비동기로 수행 —
 * 브로커가 잠깐 죽어 있어도 이 테이블에 이미 기록된 이벤트는 유실되지 않는다.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// UUID 타입 대신 String으로 둔다 — Hibernate의 UUID 컬럼 매핑 기본값(바이너리 vs 문자열)이
	// 버전마다 갈릴 수 있어, 굳이 그 모호함을 안고 갈 이유가 없다.
	@Column(name = "event_id", nullable = false, unique = true, length = 36)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 50)
	private String eventType;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Builder
	public OutboxEvent(String eventId, String eventType, String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.payload = payload;
		this.published = false;
	}

	public void markPublished() {
		this.published = true;
		this.publishedAt = LocalDateTime.now();
	}
}
