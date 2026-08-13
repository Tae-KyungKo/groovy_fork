package com.groovy.backend.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
		"/api/health",
		"/actuator/health",
		"/actuator/prometheus",
		// 브라우저 EventSource가 커스텀 헤더(Authorization)를 못 보내므로 여기만 인증 필터를 우회하고,
		// 신원은 NotificationController가 쿼리파라미터 ticket으로 직접 확인한다.
		"/api/notifications/subscribe"
	};

	// 스터디 목록/상세 조회, 전체 태그 목록 조회, 회고록 목록/상세/댓글 목록 조회는 비로그인 사용자도 접근 가능해야 하므로 GET 메서드에 한해 비인증 허용
	private static final String[] PERMIT_ALL_GET_PATTERNS = {
		"/api/studies",
		"/api/studies/{studyId}",
		"/api/tags",
		"/api/memoirs",
		"/api/memoirs/{memoirId}",
		"/api/memoirs/{memoirId}/comments"
	};

	private final TokenProvider tokenProvider;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Value("${cors.allowed-origins}")
	private String allowedOrigins;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
				// "/api/studies/{studyId}" 패턴은 단일 세그먼트 와일드카드라 "/api/studies/match"도 매칭되므로,
				// 태그 매칭 조회(JWT 필수)는 permitAll 패턴보다 먼저 명시하여 우회되지 않도록 한다.
				// "/api/memoirs/{memoirId}"도 마찬가지로 "/api/memoirs/my-studies"를 삼켜버리므로 먼저 명시한다.
				.requestMatchers(HttpMethod.GET, "/api/studies/match").authenticated()
				.requestMatchers(HttpMethod.GET, "/api/memoirs/my-studies").authenticated()
				.requestMatchers(HttpMethod.GET, "/api/memoirs/mine").authenticated()
				.requestMatchers(HttpMethod.GET, PERMIT_ALL_GET_PATTERNS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(handler -> handler.authenticationEntryPoint(jwtAuthenticationEntryPoint))
			// 회고록 목록/상세는 비회원도 보되 좋아요 여부는 로그인 사용자마다 달라야 해서, 토큰이 없을 때
			// @AuthenticationPrincipal이 스프링 시큐리티 기본 익명 principal("anonymousUser")로 채워지지
			// 않고 null이 되도록 익명 인증 필터를 끈다. permitAll 자체는 인증 객체 유무와 무관하게 동작한다.
			.anonymous(AbstractHttpConfigurer::disable)
			.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// apiFetch가 credentials: "include"로 요청하므로 Allow-Origin은 "*"를 쓸 수 없고,
	// allowCredentials(true)와 함께 명시적인 출처 목록을 지정해야 한다.
	private CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
