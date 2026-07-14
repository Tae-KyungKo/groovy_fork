package com.groovy.backend.domain.tag.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.tag.UserTag;

public interface UserTagRepository extends JpaRepository<UserTag, Long> {

	List<UserTag> findByUserId(Long userId);

	void deleteAllByUserId(Long userId);
}
