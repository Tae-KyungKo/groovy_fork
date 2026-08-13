package com.groovy.backend.domain.notification.event;

/**
 * 회고록에 좋아요가 눌렸을 때, 그 회고록 작성자 1명에게 보낼 알림 이벤트.
 * 작성자 본인이 자기 회고록에 누른 좋아요는 애초에 이 이벤트 자체가 발행되지 않는다
 * (MemoirService에서 필터링). 이미 좋아요를 누른 상태에서 다시 호출해도(멱등) 새 좋아요가
 * 아니므로 이벤트가 발행되지 않는다.
 */
public record MemoirLikeAddedEvent(
	Long recipientUserId,
	String likerName,
	Long memoirId,
	String memoirTitle
) {
}
