package com.groovy.backend.domain.tag.dto;

import com.groovy.backend.domain.tag.Tag;
import com.groovy.backend.domain.tag.TagCategory;

public record TagResponse(Long id, String name, TagCategory category) {

	public static TagResponse from(Tag tag) {
		return new TagResponse(tag.getId(), tag.getName(), tag.getCategory());
	}
}
