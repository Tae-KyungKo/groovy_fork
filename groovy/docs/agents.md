# 프로젝트 개발 지침서 (Groovy Backend Agency Guide)

이 문서는 AI 에이전트(Claude Code 등)가 "Groovy" 프로젝트의 백엔드 서비스를 개발할 때 반드시 준수해야 하는 가이드라인, 아키텍처 제약조건 및 API 명세서입니다. 구현 시 임의로 설계를 변경하지 말고 본 문서의 규칙을 엄격히 따르십시오.

---

## 1. 프로젝트 개요 및 목적
*   **프로젝트명:** Groovy (온라인 스터디 그룹 웹 플랫폼)
*   **개발 목표:** 핵심 비즈니스 로직의 신속한 구현 및 인프라/클라우드 연결 중심의 환경 구축.
*   **환경 상태:** 현재 프로젝트 레포지토리는 Private 상태이나, 코드 베이스는 향후 언제든 Public 전환 및 클라우드 CI/CD 환경에 배포될 수 있음을 전제로 엄격한 보안 원칙을 따른다.
*   **핵심 기조:** 복잡한 도메인 설계보다는 **확장 가능하고 단순한 구조**를 지향하며, 추후 클라우드 배포(AWS 등) 및 인프라 확장이 용이하도록 완전한 무상태(Stateless) 아키텍처를 유지한다.

---

## 2. 기술 스택 및 아키텍처 제약조건

### 2.1. 인프라 및 핵심 프레임워크
*   **Language & Runtime:** Java 21
*   **Framework:** Spring Boot 4.1.0
*   **Build Tool:** Gradle
*   **Database:** 관계형 데이터베이스 MySQL
*   **Configuration:** 환경설정은 계층 구조가 명확한 `src/main/resources/application.yml`을 사용한다. (properties 사용 금지)

### 2.2. 인증 및 보안 전략 (Security)
*   **JWT (JSON Web Token) 도입:** 인증 메커니즘은 JWT 토큰 방식을 사용한다.
    *   로그인 성공 시 Access Token을 발급하며, 모든 보호된 자원 API 요청은 HTTP Header(`Authorization: Bearer <Token>`)를 통해 인증을 수행한다.
*   **시크릿 키 관리 원칙:** JWT Secret Key, 데이터베이스 비밀번호 등 민감한 정보는 **절대 `application.yml`이나 코드 내에 하드코딩하지 않는다.
*   **무상태성 (Stateless):** 서버는 HTTP 세션을 절대 생성하거나 상태를 저장하지 않는다.
*   **확장성 고려:** 추후 OAuth2 소셜 로그인 방식을 추가할 예정이므로, 회원(User) 엔티티 및 인증 서비스 계층은 일반 로그인(LOCAL) 유저와 소셜 로그인 유저를 구분할 수 있는 확장 가능한 구조(예: `ProviderType` Enum 도입 등)로 설계하라.

### 2.3. 아키텍처 및 설계 원칙 (트레이드오프)
*   **레이어드 아키텍처:** 표준적인 `Controller` - `Service` - `Repository` - `Entity/DTO` 구조를 준수한다.
*   **데이터 패러다임:** 스터디 성향 태그(공부 방식, 운영 방식 등)는 다 대 다(N:M) 관계성이 발생하므로 RDB 매핑 시 성능 저하를 방지하기 위해 중개 엔티티(Mapping Entity)를 명시적으로 구현하여 1:N, N:1 관계로 풀어낸다.
*   **트랜잭션 보장:** 비즈니스 로직이 수행되는 Service 레이어에는 반드시 `@Transactional`을 선언하여 데이터 일관성을 보장하고, 단순 조회 메서드는 `readOnly = true`를 적용하여 성능을 최적화한다.

---

## 3. 기능적 요구사항 및 도메인 정의
### 3.1. 회원 관리 (Auth / User)
*   회원가입, JWT 기반 로그인, 로그아웃을 제공한다.
*   마이페이지를 통해 현재 로그인한 사용자 본인의 정보를 조회할 수 있어야 한다.

### 3.2. 스터디 그룹 모집 및 신청 (Study Group)
*   **그룹 관리:** 방장이 스터디 그룹을 생성, 수정, 삭제할 수 있다. 목록 조회 시 페이징 혹은 리스트 형태로 반환하며, 상세 조회가 가능해야 한다.
*   **신청 프로세스:** 일반 회원은 스터디 그룹에 참여 신청 및 신청 취소를 할 수 있다. 방장은 본인 그룹의 신청 목록을 조회하고, 이를 승인(APPROVED) 또는 거절(REJECTED)할 수 있다. 단, 참여 신청 데이터는 상태 값을 가진 독립된 엔티티로 관리한다.

### 3.3. 캘린더 (Calendar)
*   사용자는 자신의 개인 일정을 추가할 수 있다.
*   캘린더 조회 시, 사용자가 참여 중인 스터디 그룹의 공식 일정과 본인이 추가한 개인 일정이 통합되어 조회되어야 한다.

### 3.4. 스터디 성향 태그 (Tag Matching)
*   스터디 그룹 생성 시 그룹의 성향 태그(공부 방식, 운영 방식 등)를 지정한다.
*   유저는 마이페이지 혹은 프로필 설정을 통해 자신의 선호 태그를 선택할 수 있다.
*   전체 태그 목록 조회가 가능해야 하며, 유저의 선호 태그와 스터디 그룹 태그 간의 매핑을 통한 매칭도 조회(추천 점수 등) API가 동작해야 한다.

---

## 4. API 표준 명세서

모든 API 응답은 일관된 공통 응답 포맷(예: `ApiResponse<T>`)으로 감싸서 반환하며, 예외 발생 시 적절한 HTTP Status Code와 에러 메시지를 반환한다.

### 4.1. 인증 및 회원 API
| 기능 | HTTP 메서드 | 엔드포인트 | 비고 |
| :--- | :--- | :--- | :--- |
| 회원가입 | POST | `/api/auth/signup` | |
| 로그인 | POST | `/api/auth/login` | 성공 시 JWT 토큰 반환 |
| 로그아웃 | POST | `/api/auth/logout` | 토큰 무효화 처리 고려 |
| 마이페이지 조회 | GET | `/api/users/me` | HTTP Header의 JWT 기반 유저 식별 |

### 4.2. 스터디 그룹 및 신청 API
| 기능 | HTTP 메서드 | 엔드포인트 | 비고 |
| :--- | :--- | :--- | :--- |
| 스터디 그룹 생성 | POST | `/api/studies` | JWT 필수 |
| 스터디 목록 조회 | GET | `/api/studies` | 검색/필터링 쿼리 파라미터 고려 |
| 스터디 상세 조회 | GET | `/api/studies/{studyId}` | |
| 스터디 수정 | PUT | `/api/studies/{studyId}` | 방장 권한 검증 (JWT) |
| 스터디 삭제 | DELETE | `/api/studies/{studyId}` | 방장 권한 검증 (JWT) |
| 스터디 참여 신청 | POST | `/api/studies/{studyId}/applications` | 중복 신청 방지 로직 포함 (JWT) |
| 참여 신청 취소 | DELETE | `/api/studies/{studyId}/applications` | 본인 신청만 취소 가능 (JWT) |
| 신청 목록 조회 | GET | `/api/studies/{studyId}/applications` | 방장 전용 권한 (JWT) |
| 신청 승인/거절 | PATCH | `/api/studies/{studyId}/applications/{appId}` | Body로 상태(APPROVED/REJECTED) 전달 |

### 4.3. 캘린더 및 태그 API
| 기능 | HTTP 메서드 | 엔드포인트 | 비고 |
| :--- | :--- | :--- | :--- |
| 스터디/개인 일정 조회 | GET | `/api/calendars` | 기간 검색 파라미터 지원 및 데이터 결합 처리 (JWT) |
| 개인 일정 추가 | POST | `/api/calendars` | JWT 필수 |
| 전체 태그 목록 조회 | GET | `/api/tags` | |
| 태그 기반 매칭도 조회 | GET | `/api/studies/match` | 로그인 유저 태그 vs 스터디 태그 매칭 계산 |

---

## 5. 에이전트 행동 지침 (AI Implementation Rules)
1.  **Spring Boot 4.1+ 보안 구성:** 최근 가이드라인에 맞추어 `SecurityFilterChain` 빈 설정을 구현하되, 더 이상 사용되지 않는 구형 메서드 체이닝을 지양하고 함수형/람다식 설정을 적용하라.
2.  **예외 처리:** `RestControllerAdvice`를 사용하여 글로벌 예외 처리를 반드시 구현하라. 유효하지 않거나 만료된 JWT 토큰 접근 시 적절한 커스텀 에러 응답을 반환해야 한다.
3.  **검증(Validation):** Controller 진입 시 `@Valid`를 사용하여 입력 데이터(`RequestBody`)의 유효성 검증을 필수로 수행하라.
4.  **YAML 포맷팅:** `application.yml`을 작성하거나 수정할 때 가독성을 위해 들여쓰기(2칸 공백) 규칙을 엄격히 준수하라. 환경 변수 참조 형식(`${ENV_VAR}`)을 적극 활용하라.