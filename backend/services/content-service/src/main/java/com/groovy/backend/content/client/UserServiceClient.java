package com.groovy.backend.content.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.groovy.backend.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(content-service 추출): study-service의 UserServiceClient와 동일한 패턴. 작성자/댓글
 * 작성자 이름 같은 표시용 이름은 이 서비스에 User 테이블이 없으므로 identity-service에
 * 물어봐야 한다(identity-service UserController#getNames 참고).
 *
 * identity-service가 죽어 있으면 이름 없이("null") 나머지 데이터라도 보여주는 편이, 회고록
 * 목록 전체가 500으로 죽는 것보다 낫다고 판단해 예외를 삼키고 빈 Map을 반환한다.
 */
@Slf4j
@Component
public class UserServiceClient {

	private final RestClient restClient;

	public UserServiceClient(
		@Value("${identity-service.url:http://identity-service:8081}") String identityServiceUrl,
		@Value("${identity-service.connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${identity-service.read-timeout-ms:3000}") long readTimeoutMs
	) {
		var httpClient = java.net.http.HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		this.restClient = RestClient.builder()
			.baseUrl(identityServiceUrl)
			.requestFactory(requestFactory)
			.build();
	}

	public Map<Long, String> findNamesByIds(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}

		try {
			ApiResponse<Map<Long, String>> response = restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/users/names").queryParam("ids", userIds).build())
				.retrieve()
				.body(new ParameterizedTypeReference<ApiResponse<Map<Long, String>>>() {
				});
			return response == null || response.data() == null ? Map.of() : response.data();
		} catch (Exception e) {
			log.error("identity-service 유저 이름 조회 실패, 빈 결과로 대체: userIds={}", userIds, e);
			return Map.of();
		}
	}
}
