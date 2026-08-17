package com.groovy.backend.global.auth.jwt;

import java.util.Date;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.groovy.backend.domain.user.RoleType;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환 Phase 10: HMAC 공유 시크릿 대신 JwtKeyProvider가 들고 있는 RSA 키 쌍으로 서명/검증한다.
 * 서명은 개인키로만 할 수 있고(여기, legacy-monolith), 다른 서비스는 공개키(JWKS)로 검증만
 * 한다 — TokenProvider가 개인키를 아는 유일한 컴포넌트라는 뜻이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenProvider {

	private static final String USER_ID_CLAIM_KEY = "uid";
	private static final String ROLE_CLAIM_KEY = "role";
	private static final long ACCESS_TOKEN_EXPIRE_TIME_MILLIS = 1000L * 60 * 60; // 1시간

	private final JwtKeyProvider jwtKeyProvider;

	public String createToken(String email, Long userId, RoleType role) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + ACCESS_TOKEN_EXPIRE_TIME_MILLIS);

		return Jwts.builder()
			// kid를 헤더에 실어야, 검증하는 쪽(JWKS를 여러 개 가진 미래의 Key Rotation 상황)이
			// 어떤 공개키로 검증해야 할지 알 수 있다.
			.header().keyId(jwtKeyProvider.keyId()).and()
			.subject(email)
			.claim(USER_ID_CLAIM_KEY, userId)
			.claim(ROLE_CLAIM_KEY, role.name())
			.issuedAt(now)
			.expiration(expiry)
			.signWith(jwtKeyProvider.privateKey(), Jwts.SIG.RS256)
			.compact();
	}

	public boolean validateToken(String token) {
		try {
			Jwts.parser().verifyWith(jwtKeyProvider.publicKey()).build().parseSignedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			log.warn("토큰 검증 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
			return false;
		}
	}

	public Authentication getAuthentication(String token) {
		Claims claims = parseClaims(token);
		String email = claims.getSubject();
		String role = claims.get(ROLE_CLAIM_KEY, String.class);

		List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

		return new UsernamePasswordAuthenticationToken(email, null, authorities);
	}

	private Claims parseClaims(String token) {
		return Jwts.parser().verifyWith(jwtKeyProvider.publicKey()).build().parseSignedClaims(token).getPayload();
	}
}
