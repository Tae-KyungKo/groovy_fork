package com.groovy.backend.domain.tag.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.tag.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {
}
