package com.groovy.backend.global.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Key;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;

/**
 * Phase 13: 남아있는 유일한 서비스 간 동기 API(GET /.well-known/jwks.json,
 * notification-service의 JwksKeyLocator가 소비)에 대한 Provider 쪽 계약 테스트.
 *
 * Spring Cloud Contract 대신, 실제 컨슈머가 쓰는 라이브러리(jjwt의 Jwks.setParser())로
 * JwksController의 실제 응답을 직접 파싱해서 "진짜로 소비 가능한지" 검증한다 — Spring Cloud는
 * Boot 4.0.7까지만 검증돼 있다는 걸 Phase 5/11에서 이미 겪어서 또 그 버전 충돌 위험을 지려는
 * 대신 택한 방식이다. 이 테스트는 Phase 10에서 실제로 겪은 버그(JwkSet을 그대로 반환하면
 * Jackson이 {"keys":{}}로 잘못 직렬화하던 문제)의 회귀 방지 역할도 겸한다.
 */
class JwksControllerContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void jwks_응답을_jjwt_파서로_그대로_소비할_수_있다() throws Exception {
		JwtKeyProvider keyProvider = new JwtKeyProvider();
		JwksController controller = new JwksController(keyProvider);

		Map<String, Object> body = controller.jwks();
		String json = objectMapper.writeValueAsString(body);

		JwkSet jwkSet = Jwks.setParser().build().parse(json);

		assertThat(jwkSet.getKeys()).hasSize(1);
		Jwk<?> jwk = jwkSet.getKeys().iterator().next();
		assertThat(jwk.getId()).isEqualTo(keyProvider.keyId());
		assertThat(jwk.get("kty")).isEqualTo("RSA");

		Key key = jwk.toKey();
		assertThat(key).isEqualTo(keyProvider.publicKey());
	}
}
