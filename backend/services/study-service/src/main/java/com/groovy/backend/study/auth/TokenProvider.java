package com.groovy.backend.study.auth;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(study-service 추출): notification-service의 TokenProvider와 동일한 패턴.
 * JwksKeyLocator가 identity-service의 공개키로 서명을 검증만 한다. principal을 email이 아니라
 * userId(Long)로 바로 채운다 — 이 서비스에는 User 테이블이 없어서 email로는 아무것도 조회할 수
 * 없고, uid 클레임이 이미 신원 그 자체이기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenProvider {

	private static final String USER_ID_CLAIM_KEY = "uid";
	private static final String ROLE_CLAIM_KEY = "role";

	private final JwksKeyLocator jwksKeyLocator;

	public boolean validateToken(String token) {
		try {
			Jwts.parser().keyLocator(jwksKeyLocator).build().parseSignedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			log.warn("토큰 검증 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
			return false;
		}
	}

	public Authentication getAuthentication(String token) {
		Claims claims = parseClaims(token);
		Long userId = claims.get(USER_ID_CLAIM_KEY, Long.class);
		String role = claims.get(ROLE_CLAIM_KEY, String.class);

		List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

		return new UsernamePasswordAuthenticationToken(userId, null, authorities);
	}

	private Claims parseClaims(String token) {
		return Jwts.parser().keyLocator(jwksKeyLocator).build().parseSignedClaims(token).getPayload();
	}
}
