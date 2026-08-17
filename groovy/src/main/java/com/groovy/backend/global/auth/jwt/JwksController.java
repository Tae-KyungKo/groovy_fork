package com.groovy.backend.global.auth.jwt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.security.Jwk;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환 Phase 10: 다른 서비스가 여기서 공개키를 가져가 JWT 서명을 검증한다("/.well-known/..."는
 * 이런 메타데이터를 공개하는 흔한 관례 경로). 인증 없이 완전히 열려 있어야 한다 — "공개"키라는
 * 이름 그대로다(SecurityConfig의 permitAll 목록에 반드시 포함).
 *
 * jjwt의 JwkSet/Jwk를 그대로 반환하면(둘 다 Map을 구현하긴 하지만) Boot 4.1의 Jackson이
 * 내부 구조를 못 알아보고 빈 객체({"keys":{}})로 직렬화해버리는 걸 실제로 겪었다 — 그래서
 * 순수 LinkedHashMap/List로 한 번 더 감싸서 반환한다.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

	private final JwtKeyProvider jwtKeyProvider;

	@GetMapping("/.well-known/jwks.json")
	public Map<String, Object> jwks() {
		List<Map<String, Object>> keys = jwtKeyProvider.jwkSet().getKeys().stream()
			.map(this::toPlainMap)
			.toList();
		return Map.of("keys", keys);
	}

	private Map<String, Object> toPlainMap(Jwk<?> jwk) {
		return new LinkedHashMap<>(jwk);
	}
}
