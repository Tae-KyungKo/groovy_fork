package com.groovy.backend.global.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.groovy.backend.domain.user.RoleType;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class TokenProvider {

	private static final String ROLE_CLAIM_KEY = "role";
	private static final long ACCESS_TOKEN_EXPIRE_TIME_MILLIS = 1000L * 60 * 60; // 1시간

	private final SecretKey key;

	// 무상태 서명 키는 시스템 환경 변수(JWT_SECRET_KEY)로부터 빌드 시점에 동적으로 생성한다.
	public TokenProvider(@Value("${jwt.secret-key}") String secretKey) {
		this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
	}

	public String createToken(String email, RoleType role) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + ACCESS_TOKEN_EXPIRE_TIME_MILLIS);

		return Jwts.builder()
			.subject(email)
			.claim(ROLE_CLAIM_KEY, role.name())
			.issuedAt(now)
			.expiration(expiry)
			.signWith(key)
			.compact();
	}

	public boolean validateToken(String token) {
		try {
			Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
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
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}
