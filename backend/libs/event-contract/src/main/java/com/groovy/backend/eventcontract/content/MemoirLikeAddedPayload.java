package com.groovy.backend.eventcontract.content;

/** content-service 발행, notification-service 소비. groovy(레거시)의 MemoirLikeAddedEvent와 대응. */
public record MemoirLikeAddedPayload(
	Long recipientUserId,
	String likerName,
	Long memoirId,
	String memoirTitle
) {
}
