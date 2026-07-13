package com.groovy.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.groovy.backend.global.auth.jwt.JwtAuthenticationEntryPoint;
import com.groovy.backend.global.auth.jwt.JwtAuthenticationFilter;
import com.groovy.backend.global.auth.jwt.TokenProvider;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String[] PERMIT_ALL_PATTERNS = {
		"/api/auth/signup",
		"/api/auth/login",
		"/api/health"
	};

	// 스터디 목록/상세 조회, 전체 태그 목록 조회는 비로그인 사용자도 접근 가능해야 하므로 GET 메서드에 한해 비인증 허용
	private static final String[] PERMIT_ALL_GET_PATTERNS = {
		"/api/studies",
		"/api/studies/{studyId}",
		"/api/tags"
	};

	private final TokenProvider tokenProvider;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
				// "/api/studies/{studyId}" 패턴은 단일 세그먼트 와일드카드라 "/api/studies/match"도 매칭되므로,
				// 태그 매칭 조회(JWT 필수)는 permitAll 패턴보다 먼저 명시하여 우회되지 않도록 한다.
				.requestMatchers(HttpMethod.GET, "/api/studies/match").authenticated()
				.requestMatchers(HttpMethod.GET, PERMIT_ALL_GET_PATTERNS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(handler -> handler.authenticationEntryPoint(jwtAuthenticationEntryPoint))
			.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
