package com.groovy.backend.eventcontract.study;

/**
 * 초안(§3-2) — study-service 발행 예정, 현재 소비자 없음. 발행 코드는 아직 작성하지 않는다.
 * 소비자가 실제로 필요해지는 시점(예: 검색/추천 서비스)에 study-service에 발행 로직을 추가한다.
 */
public record StudyCreatedPayload(
	Long studyId,
	String studyTitle,
	Long leaderId
) {
}
