package com.groovy.backend.domain.user.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.user.ProviderType;
import com.groovy.backend.domain.user.RoleType;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.dto.LoginRequest;
import com.groovy.backend.domain.user.dto.LoginResponse;
import com.groovy.backend.domain.user.dto.SignupRequest;
import com.groovy.backend.domain.user.dto.UserResponse;
import com.groovy.backend.domain.user.repository.UserRepository;
import com.groovy.backend.global.auth.jwt.TokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenProvider tokenProvider;

	@Transactional
	public UserResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			log.warn("회원가입 실패(이메일 중복): email={}", request.email());
			throw new IllegalArgumentException("이미 가입된 이메일입니다.");
		}

		User user = User.builder()
			.email(request.email())
			.password(passwordEncoder.encode(request.password()))
			.name(request.name())
			.providerType(ProviderType.LOCAL)
			.roleType(RoleType.USER)
			.build();

		User saved = userRepository.save(user);
		log.info("회원가입 성공: email={}, userId={}", saved.getEmail(), saved.getId());

		return UserResponse.from(saved);
	}

	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
			.orElseThrow(() -> {
				log.warn("로그인 실패(존재하지 않는 이메일): email={}", request.email());
				return new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
			});

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			log.warn("로그인 실패(비밀번호 불일치): email={}", request.email());
			throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
		}

		String accessToken = tokenProvider.createToken(user.getEmail(), user.getId(), user.getRoleType());
		log.info("로그인 성공: email={}, userId={}", user.getEmail(), user.getId());
		return LoginResponse.of(accessToken);
	}

	public UserResponse getMyInfo(String email) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 유저: email={}", email);
				return new IllegalArgumentException("존재하지 않는 유저입니다.");
			});

		return UserResponse.from(user);
	}

	// 다른 도메인 서비스가 User를 조회할 때 쓰는 공개 API. UserRepository를 다른 도메인에 직접
	// 노출하지 않고, 이 메서드를 거치도록 강제해 "누가 User 데이터에 접근하는지"를 한 곳으로 모은다.
	public Optional<User> findByEmail(String email) {
		return userRepository.findByEmail(email);
	}

	public Optional<User> findById(Long userId) {
		return userRepository.findById(userId);
	}
}
