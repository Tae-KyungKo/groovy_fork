package com.groovy.backend.calendar.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.groovy.backend.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(calendar-service 추출): groovy(레거시)의 global.client.StudyServiceClient를 그대로
 * 복사해온 것 — Study가 이미 study-service로 빠져나가 있어서 groovy와 동일하게 동기 HTTP로만
 * 접근할 수 있다. groovy 원본은 Memoir가 계속 쓰므로 그대로 남아있고, 이건 별도 프로세스라
 * 각자 복사본을 갖는다(study-service의 client 패키지와 동일한 이유). Calendar가 실제로 쓰는
 * 3개 메서드(getStudy/getApprovedMemberUserIds/getMyStudyOptions)만 옮겼다 —
 * getStudySummaries/addExp는 Memoir 전용이라 제외.
 *
 * 원 요청의 Authorization 헤더를 그대로 study-service에 전달한다 — study-service의
 * "/api/studies/{id}"가 로그인 사용자별로 myApplicationStatus를 채워주므로, 그 값으로
 * "이 유저가 이 스터디 멤버인지"를 판단할 수 있다(별도의 멤버십 확인 API를 새로 만들지 않는다).
 */
@Slf4j
@Component
public class StudyServiceClient {

	private final RestClient restClient;

	public StudyServiceClient(
		@Value("${study-service.url:http://study-service:8082}") String studyServiceUrl,
		@Value("${study-service.connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${study-service.read-timeout-ms:3000}") long readTimeoutMs
	) {
		var httpClient = java.net.http.HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		this.restClient = RestClient.builder()
			.baseUrl(studyServiceUrl)
			.requestFactory(requestFactory)
			.build();
	}

	// study-service의 공개 API("/api/studies/{id}")를 그대로 재사용한다. 없는 스터디거나
	// study-service가 죽어 있으면 빈 Optional을 반환한다 — 호출부가 "존재하지 않는 스터디"로
	// 처리한다.
	public Optional<StudyView> getStudy(Long studyId) {
		try {
			ApiResponse<StudyView> response = restClient.get()
				.uri("/api/studies/{id}", studyId)
				.headers(this::forwardAuthorization)
				.retrieve()
				.body(new ParameterizedTypeReference<ApiResponse<StudyView>>() {
				});
			return Optional.ofNullable(response == null ? null : response.data());
		} catch (Exception e) {
			log.error("study-service 스터디 조회 실패: studyId={}", studyId, e);
			return Optional.empty();
		}
	}

	// 캘린더에서 "스터디 약속" 등록 시 고를 수 있는 "내가 방장이거나 승인되어 속한 스터디" 옵션
	// 목록. study-service의 기존 공개 API 2개("/api/users/me/studies", "/api/users/me/applications")를
	// 그대로 재사용해 합치고 studyId 기준으로 중복 제거한다 — 이 조합만을 위한 내부 API를 새로
	// 만들지 않는다.
	public List<StudyOptionView> getMyStudyOptions() {
		Map<String, StudyOptionView> optionsById = new LinkedHashMap<>();

		try {
			ApiResponse<List<MyStudyView>> ledResponse = restClient.get()
				.uri("/api/users/me/studies")
				.headers(this::forwardAuthorization)
				.retrieve()
				.body(new ParameterizedTypeReference<ApiResponse<List<MyStudyView>>>() {
				});
			if (ledResponse != null && ledResponse.data() != null) {
				ledResponse.data().forEach(study -> optionsById.put(study.id(), new StudyOptionView(study.id(), study.title(), true)));
			}
		} catch (Exception e) {
			log.error("study-service 방장 스터디 목록 조회 실패", e);
		}

		try {
			ApiResponse<List<MyApplicationView>> applicationsResponse = restClient.get()
				.uri("/api/users/me/applications")
				.headers(this::forwardAuthorization)
				.retrieve()
				.body(new ParameterizedTypeReference<ApiResponse<List<MyApplicationView>>>() {
				});
			if (applicationsResponse != null && applicationsResponse.data() != null) {
				applicationsResponse.data().stream()
					.filter(application -> "APPROVED".equals(application.status()))
					.forEach(application -> optionsById.put(application.studyId(), new StudyOptionView(application.studyId(), application.studyTitle(), false)));
			}
		} catch (Exception e) {
			log.error("study-service 신청 내역 조회 실패", e);
		}

		return List.copyOf(optionsById.values());
	}

	// Calendar가 스터디 일정 변경 알림 수신자(승인된 멤버 전원)를 구할 때 쓴다
	// (study-service StudyController#getApprovedMemberIds가 소비하는 내부 API).
	public List<Long> getApprovedMemberUserIds(Long studyId) {
		try {
			ApiResponse<List<Long>> response = restClient.get()
				.uri("/api/studies/{id}/members", studyId)
				.headers(this::forwardAuthorization)
				.retrieve()
				.body(new ParameterizedTypeReference<ApiResponse<List<Long>>>() {
				});
			return response == null || response.data() == null ? List.of() : response.data();
		} catch (Exception e) {
			log.error("study-service 승인 멤버 목록 조회 실패: studyId={}", studyId, e);
			return List.of();
		}
	}

	private void forwardAuthorization(HttpHeaders headers) {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return;
		}
		HttpServletRequest request = attributes.getRequest();
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorizationHeader != null) {
			headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record StudyView(String id, String leaderId, String title, String myApplicationStatus) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MyStudyView(String id, String title) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MyApplicationView(String studyId, String studyTitle, String status) {
	}

	public record StudyOptionView(String studyId, String title, boolean isLeader) {
	}
}
