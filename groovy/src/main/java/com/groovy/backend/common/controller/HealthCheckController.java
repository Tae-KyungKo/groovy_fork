package com.groovy.backend.common.controller;

import com.groovy.backend.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

	@GetMapping("/api/health")
	public ApiResponse<Void> health() {
		return ApiResponse.of("UP", "Groovy Backend Phase 1 Active");
	}
}
