package com.groovy.backend.domain.user.service;

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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenProvider tokenProvider;

	@Transactional
	public void signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new IllegalArgumentException("이미 가입된 이메일입니다.");
		}

		User user = User.builder()
			.email(request.email())
			.password(passwordEncoder.encode(request.password()))
			.name(request.name())
			.providerType(ProviderType.LOCAL)
			.roleType(RoleType.USER)
			.build();

		userRepository.save(user);
	}

	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
			.orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
		}

		String accessToken = tokenProvider.createToken(user.getEmail(), user.getRoleType());
		return LoginResponse.of(accessToken);
	}

	public UserResponse getMyInfo(String email) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

		return UserResponse.from(user);
	}
}
