package com.groovy.backend.domain.user.dto;

import com.groovy.backend.domain.user.ProviderType;
import com.groovy.backend.domain.user.RoleType;
import com.groovy.backend.domain.user.User;

public record UserResponse(Long id, String email, String name, ProviderType providerType, RoleType roleType) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getProviderType(), user.getRoleType());
	}
}
