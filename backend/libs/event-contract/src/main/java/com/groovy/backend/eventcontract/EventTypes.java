package com.groovy.backend.eventcontract;

/**
 * EventEnvelope.eventType에 쓰는 표준 이벤트명.
 * Groovy_MSA_Phase2_서비스경계와Contract.md §3-1(이미 구현됨), §3-2(초안, 발행 코드 없음)를
 * 그대로 상수화했다. 이 목록에 새 이벤트를 추가할 때는 그 문서도 함께 갱신한다.
 */
public final class EventTypes {

	// --- §3-1: 이미 Spring ApplicationEvent로 구현되어 있는 이벤트 ---
	public static final String STUDY_APPLICATION_SUBMITTED = "STUDY_APPLICATION_SUBMITTED";
	public static final String STUDY_APPLICATION_APPROVED = "STUDY_APPLICATION_APPROVED";
	public static final String STUDY_APPLICATION_REJECTED = "STUDY_APPLICATION_REJECTED";
	public static final String STUDY_SEAT_AVAILABLE = "STUDY_SEAT_AVAILABLE";
	public static final String STUDY_LEVEL_UP = "STUDY_LEVEL_UP";
	public static final String STUDY_SCHEDULE_CHANGED = "STUDY_SCHEDULE_CHANGED";
	public static final String MEMOIR_COMMENT_ADDED = "MEMOIR_COMMENT_ADDED";
	public static final String MEMOIR_LIKE_ADDED = "MEMOIR_LIKE_ADDED";

	// --- §3-2: 스키마만 확정, 발행 코드는 아직 없음(소비자가 생기는 시점에 구현) ---
	public static final String USER_DELETED = "USER_DELETED";
	public static final String STUDY_CREATED = "STUDY_CREATED";
	public static final String STUDY_MEMBER_LEFT = "STUDY_MEMBER_LEFT";

	private EventTypes() {
	}
}
