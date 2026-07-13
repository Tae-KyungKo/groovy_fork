package com.groovy.backend.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * spring.threads.virtual.enabled=true 상태에서 대량 동시 요청이 몰려도
 * 톰캣/HikariCP가 요청을 소화하는지 확인하는 경량 부하 테스트.
 * 실제 임베디드 서버를 띄우고, 가상 스레드 executor로 짧은 시간에
 * 헬스체크 엔드포인트를 동시 호출하여 전부 200 OK를 받는지 검증한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConcurrencyTest {

	private static final int REQUEST_COUNT = 500;

	@LocalServerPort
	private int port;

	@Test
	void handlesHundredsOfConcurrentHealthRequests() throws Exception {
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
		URI uri = URI.create("http://localhost:" + port + "/api/health");

		List<Integer> statusCodes;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Integer>> futures = IntStream.range(0, REQUEST_COUNT)
				.mapToObj(i -> executor.submit(() -> sendHealthCheck(client, uri)))
				.toList();

			statusCodes = futures.stream()
				.map(this::getQuietly)
				.toList();
		}

		assertThat(statusCodes).hasSize(REQUEST_COUNT);
		assertThat(statusCodes).allMatch(status -> status == 200);
	}

	private int sendHealthCheck(HttpClient client, URI uri) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
		HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
		return response.statusCode();
	}

	private Integer getQuietly(Future<Integer> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new IllegalStateException("동시 요청 처리 중 예외 발생", e);
		}
	}
}
