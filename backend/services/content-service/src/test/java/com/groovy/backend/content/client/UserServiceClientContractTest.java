package com.groovy.backend.content.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * MSA 전환(content-service 추출): study-service의 UserServiceClientContractTest와 동일한
 * 방식(JDK 내장 HttpServer 스텁)으로, content-service의 UserServiceClient가 identity-service의
 * "GET /api/users/names" 응답 모양을 정확히 소비하는지 검증한다.
 */
class UserServiceClientContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer stubServer;

	@AfterEach
	void tearDown() {
		if (stubServer != null) {
			stubServer.stop(0);
		}
	}

	@Test
	void identity_service의_names_응답_모양을_흉내낸_스텁에서_UserServiceClient로_이름_맵을_읽을_수_있다() throws Exception {
		String responseJson = objectMapper.writeValueAsString(Map.of(
			"status", "SUCCESS",
			"message", "유저 이름 조회에 성공했습니다.",
			"data", Map.of("1", "홍길동", "2", "김철수")
		));
		int port = startStubServer(responseJson);

		UserServiceClient client = new UserServiceClient("http://localhost:" + port, 2000, 3000);

		Map<Long, String> names = client.findNamesByIds(java.util.List.of(1L, 2L));

		assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "홍길동", 2L, "김철수"));
	}

	@Test
	void identity_service가_죽어있으면_예외_대신_빈_맵을_반환한다() {
		UserServiceClient client = new UserServiceClient("http://localhost:1", 200, 200);

		Map<Long, String> names = client.findNamesByIds(java.util.List.of(1L));

		assertThat(names).isEmpty();
	}

	private int startStubServer(String responseBody) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/api/users/names", exchange -> {
			byte[] bytes = responseBody.getBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		this.stubServer = server;
		TimeUnit.MILLISECONDS.sleep(50);
		return server.getAddress().getPort();
	}
}
