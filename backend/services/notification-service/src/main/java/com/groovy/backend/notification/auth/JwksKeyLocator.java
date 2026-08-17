package com.groovy.backend.notification.auth;

import java.net.http.HttpClient;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;

import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환 Phase 10: legacy-monolith의 JWKS 엔드포인트에서 공개키를 가져와 캐싱한다.
 *
 * Phase 11(Resilience): 이 호출이 지금 이 서비스에 남은 유일한 동기 서비스 간 호출이라
 * 여기에 적용한다.
 *   Timeout    → JdkClientHttpRequestFactory에 connect/read 타임아웃 명시(Phase 8와 동일 패턴)
 *   Retry      → 지수 백오프 + 지터(IntervalFunction.ofExponentialRandomBackoff)로 최대 3회
 *   CircuitBreaker → 연속 실패 시 legacy-monolith를 잠깐 동안 아예 호출하지 않고 빠르게 포기
 *   Fallback   → 위가 전부 실패해도 예외를 던지지 않고 "기존 캐시를 그대로 유지"한다 —
 *                legacy-monolith가 죽어 있어도 이미 알고 있는 kid는 계속 검증 가능하다
 *                (완전한 장애 전파를 막는 지점).
 */
@Slf4j
@Component
public class JwksKeyLocator implements Locator<Key> {

	private final RestClient restClient;
	private final Duration cacheTtl;
	private final CircuitBreaker circuitBreaker;
	private final Retry retry;
	private final ReentrantLock lock = new ReentrantLock();

	private volatile Map<String, Key> keysByKid = Map.of();
	private volatile Instant lastFetchedAt = Instant.EPOCH;

	public JwksKeyLocator(
		@Value("${jwt.jwks-url}") String jwksUrl,
		@Value("${jwt.jwks-connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${jwt.jwks-read-timeout-ms:3000}") long readTimeoutMs,
		@Value("${jwt.jwks-cache-ttl-seconds:300}") long cacheTtlSeconds
	) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		this.restClient = RestClient.builder().baseUrl(jwksUrl).requestFactory(requestFactory).build();
		this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);

		this.circuitBreaker = CircuitBreaker.of("jwks", CircuitBreakerConfig.custom()
			.failureRateThreshold(50)
			.slidingWindowSize(5)
			.minimumNumberOfCalls(3)
			.waitDurationInOpenState(Duration.ofSeconds(10))
			.permittedNumberOfCallsInHalfOpenState(1)
			.build());
		this.circuitBreaker.getEventPublisher().onStateTransition(
			event -> log.warn("JWKS CircuitBreaker 상태 전이: {}", event.getStateTransition()));

		this.retry = Retry.of("jwks", RetryConfig.custom()
			.maxAttempts(3)
			.intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(200), 2.0))
			.build());
		this.retry.getEventPublisher().onRetry(
			event -> log.warn("JWKS 조회 재시도: attempt={}, lastError={}",
				event.getNumberOfRetryAttempts(), event.getLastThrowable().toString()));
	}

	@Override
	public Key locate(Header header) {
		if (!(header instanceof ProtectedHeader protectedHeader)) {
			return null;
		}
		String kid = protectedHeader.getKeyId();
		if (kid == null) {
			return null;
		}

		if (isStale()) {
			refresh();
		}
		Key key = keysByKid.get(kid);
		if (key == null) {
			// 모르는 kid — 키가 막 회전됐을 수 있으니 캐시를 무시하고 한 번 더 확인한다.
			refresh();
			key = keysByKid.get(kid);
		}
		return key;
	}

	private boolean isStale() {
		return Instant.now().isAfter(lastFetchedAt.plus(cacheTtl));
	}

	private void refresh() {
		lock.lock();
		try {
			Supplier<String> fetch = () -> restClient.get().retrieve().body(String.class);
			Supplier<String> resilientFetch = CircuitBreaker.decorateSupplier(circuitBreaker,
				Retry.decorateSupplier(retry, fetch));

			String json = resilientFetch.get();
			JwkSet jwkSet = Jwks.setParser().build().parse(json);
			keysByKid = jwkSet.getKeys().stream()
				.collect(Collectors.toMap(Jwk::getId, Jwk::toKey));
			lastFetchedAt = Instant.now();
			log.info("JWKS 갱신 완료: kid={}", keysByKid.keySet());
		} catch (Exception e) {
			// Fallback: 예외를 위로 던지지 않는다. 이미 갖고 있던 캐시(비어있을 수도, 예전 키일
			// 수도 있음)를 그대로 유지한 채 조용히 리턴한다 — legacy-monolith 장애가 이 서비스의
			// 나머지 기능(이미 캐시된 kid로 검증하는 요청들)까지 전부 죽이지 않게 하는 지점이다.
			log.error("JWKS 갱신 실패, 기존 캐시({}개 키)를 유지한다", keysByKid.size(), e);
		} finally {
			lock.unlock();
		}
	}
}
