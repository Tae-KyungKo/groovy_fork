package com.groovy.backend.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	// 소셜 로그인 유저는 비밀번호가 없을 수 있어 nullable 허용
	@Column
	private String password;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProviderType providerType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RoleType roleType;

	@Builder
	public User(String email, String password, String name, ProviderType providerType, RoleType roleType) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.providerType = providerType;
		this.roleType = roleType;
	}
}
