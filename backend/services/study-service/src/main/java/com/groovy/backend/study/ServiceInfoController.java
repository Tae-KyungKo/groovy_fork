package com.groovy.backend.study;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MSA 전환 Phase 3: 아직 도메인 로직 없는 골격 확인용.
 * groovy(레거시)의 Study/Application/Waitlist(+StudyTag) 도메인이 이후 서비스 추출 단계에서
 * 이 자리로 옮겨온다.
 */
@RestController
class ServiceInfoController {

	@GetMapping("/")
	public String info() {
		return "study-service (Phase 3 골격 — 아직 도메인 로직 없음)";
	}
}
