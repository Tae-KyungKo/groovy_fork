package com.groovy.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * MSA 전환 Phase 1(Modular Monolith): 도메인이 다른 도메인의 Repository를 직접 주입받아 쓰는 것을 막는다.
 * 반드시 그 도메인의 Service 공개 API(예: UserService.findByEmail, StudyService.getStudyEntity)를
 * 거치도록 강제한다. 이 규칙을 어기면 이 테스트가 실패한다 — 계획서(Groovy_MSA_전환계획.md) Phase 1
 * 완료 기준 "cross-domain repository 직접 참조가 0건"을 CI에서 계속 검증하기 위한 장치.
 *
 * Study/Application/Waitlist는 하나의 Bounded Context로 취급하므로(Groovy_MSA_도메인경계_재검토.md 참고)
 * domain.study 패키지 내부에서 서로의 Repository를 쓰는 것은 위반이 아니다.
 */
@AnalyzeClasses(packages = "com.groovy.backend.domain")
class ModuleBoundaryTest {

	@ArchTest
	static final ArchRule other_domains_must_not_access_user_repository_directly =
		noClasses().that().resideOutsideOfPackage("..domain.user..")
			.should().dependOnClassesThat().resideInAPackage("..domain.user.repository..")
			.because("User 데이터는 UserService의 공개 API(findByEmail/findById 등)를 거쳐야 한다");

	@ArchTest
	static final ArchRule other_domains_must_not_access_study_repositories_directly =
		noClasses().that().resideOutsideOfPackage("..domain.study..")
			.should().dependOnClassesThat().resideInAPackage("..domain.study.repository..")
			.because("Study/Application/Waitlist 데이터는 StudyService/ApplicationService의 공개 API를 거쳐야 한다");
}
