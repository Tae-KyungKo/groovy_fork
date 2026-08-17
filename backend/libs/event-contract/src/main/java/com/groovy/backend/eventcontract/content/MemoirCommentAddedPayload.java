package com.groovy.backend.eventcontract.content;

/** content-service 발행, notification-service 소비. groovy(레거시)의 MemoirCommentAddedEvent와 대응. */
public record MemoirCommentAddedPayload(
	Long recipientUserId,
	String commenterName,
	Long memoirId,
	String memoirTitle
) {
}
