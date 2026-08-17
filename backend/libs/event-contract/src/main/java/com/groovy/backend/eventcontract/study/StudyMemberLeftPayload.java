package com.groovy.backend.eventcontract.study;

/**
 * 초안(§3-2) — study-service 발행 예정, 현재 소비자 없음.
 * content-service(회고록 접근권), calendar-service(스터디 일정 접근권)가 탈퇴에 반응해야 할
 * 필요가 생기면 그때 study-service의 ApplicationService.leave()에 발행 코드를 추가한다.
 */
public record StudyMemberLeftPayload(
	Long studyId,
	Long userId
) {
}
