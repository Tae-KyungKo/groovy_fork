package com.groovy.backend.domain.notification.event;

/**
 * 회고록에 댓글이 달렸을 때, 그 회고록 작성자 1명에게 보낼 알림 이벤트.
 * 작성자 본인이 자기 회고록에 단 댓글은 애초에 이 이벤트 자체가 발행되지 않는다
 * (MemoirCommentService에서 필터링).
 */
public record MemoirCommentAddedEvent(
	Long recipientUserId,
	String commenterName,
	Long memoirId,
	String memoirTitle
) {
}
