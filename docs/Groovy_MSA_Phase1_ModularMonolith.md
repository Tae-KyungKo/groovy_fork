# Groovy MSA 전환 — Phase 1: Modular Monolith 전환

> 상위 계획: [`Groovy_MSA_전환계획.md`](./Groovy_MSA_전환계획.md) Phase 1
> 선행 문서: [`Groovy_MSA_Phase0_의존성분석.md`](./Groovy_MSA_Phase0_의존성분석.md),
>            [`Groovy_MSA_도메인경계_재검토.md`](./Groovy_MSA_도메인경계_재검토.md)
> 목표: 서비스로 쪼개기 전에 패키지(도메인) 레벨에서 "남의 Repository/Entity를 직접 못 건드리게" 만든다.

## 0. 착수 전 도메인 경계 재검토

Phase 0에서 나눈 6개 도메인(Study/User/Memoir/Calendar/Notification/Tag)을 분리·통합할 필요가
있는지 다시 검토했다. **결론: 경계 변경 없음.** Study(+Application+Waitlist)는 정원 제어가 하나의
트랜잭션/락 경계에 걸쳐 있어 이미 단일 Aggregate이고, Calendar는 개인 일정이라는 독자적 존재 이유가
있어 Study에 흡수할 수 없다. 유일하게 재정의한 지점은 Tag — `StudyTag`(Study 소유)와
`UserTag`(User 소유)가 하나의 패키지에 얹혀 있다는 점을 문서화하고, "Tag를 study-service에 흡수"
결정은 Phase 2로 이관했다. 상세 근거는 [`Groovy_MSA_도메인경계_재검토.md`](./Groovy_MSA_도메인경계_재검토.md) 참고.

## 1. 적용한 규칙

Phase 0에서 찾은 🔴 cross-domain Repository 직접 접근 12곳을 아래 원칙으로 정리했다.

> **다른 도메인의 데이터가 필요하면 그 도메인의 Repository를 직접 주입받지 않고, 반드시 그 도메인
> Service의 공개 API(메서드)를 거친다.** Service는 여전히 자신의 JPA 엔티티를 반환할 수 있다 —
> DB가 아직 공유되는 이번 단계에서 엔티티 자체를 완전히 숨기는 것(DTO만 노출)은 Phase 2(Contract
> 정의)/Phase 7(DB 분리)의 몫이다. 지금 막는 것은 "누가 내 테이블을 임의로 조회하는가"이지,
> "내 엔티티 타입을 다른 모듈이 아는가"가 아니다.

예외적으로 Calendar → Study는 한 단계 더 나아가 **엔티티 참조 자체(JPA `@ManyToOne`)를 제거**했다
(계획서가 명시적으로 "Calendar → Study Entity 참조 금지"를 규칙으로 못박았기 때문 — 상세 §3).

## 2. Repository 직접 접근 제거

| 도메인 | 변경 전 | 변경 후 |
|---|---|---|
| Study(Study/Application/Waitlist Service) | `UserRepository` 직접 주입 | `UserService.findByEmail(...)` 경유 |
| Memoir(Memoir/MemoirComment Service) | `StudyRepository`, `ApplicationRepository`, `UserRepository` 직접 주입 | `StudyService.getStudyEntity/getStudiesLedBy`, `ApplicationService.isApprovedMember/getApprovedStudies`, `UserService.findByEmail` 경유 |
| Calendar | `StudyRepository`, `ApplicationRepository`, `UserRepository` 직접 주입 | 동일하게 Study/Application/User의 Service 경유로 전환 |
| Tag | `UserRepository` 직접 주입 | `UserService.findByEmail(...)` 경유 |
| Notification | `UserRepository` 직접 주입 | `UserService.findByEmail/findById(...)` 경유 |

**추가한 공개 API** (다른 도메인이 쓰도록 새로 노출한 메서드):

```
UserService
├─ findByEmail(String): Optional<User>
└─ findById(Long): Optional<User>

StudyService
├─ getStudyEntity(Long): Study            (기존 package-private → public 전환)
└─ getStudiesLedBy(Long leaderId): List<Study>   (신규)

ApplicationService
├─ isApprovedMember(Long studyId, Long userId): boolean       (신규)
├─ getApprovedMemberUserIds(Long studyId): List<Long>          (신규)
└─ getApprovedStudies(Long userId): List<Study>                (신규)
```

부수 효과로, 8개 서비스에 복붙돼 있던 `private User getUser(String email)` 조회+예외 로직의
**원본 쿼리가 UserService 한 곳으로 모였다** (각 서비스는 여전히 자기 도메인에 맞는 로그 메시지를
남기는 얇은 래퍼만 유지 — 동작은 100% 동일하게 보존).

`WaitlistService`가 `StudyRepository`를 직접 쓰는 것은 그대로 남겨뒀다 — Phase 0에서 확인했듯
`StudyService ↔ WaitlistService` 순환 의존을 피하기 위한 의도적 설계이고, `study` 패키지 내부의
접근이라 "cross-domain"이 아니다(§0 도메인 경계 재검토와 일치).

## 3. Calendar → Study 엔티티 참조 제거 (ID 참조 전환)

계획서가 예시로 명시한 "Calendar → Study Entity 참조 금지"를 실제로 적용한 유일한 지점.

- `Calendar.study` (`@ManyToOne` JPA 연관관계) → `Calendar.studyId` (`Long`, 평범한 컬럼)로 전환.
  DB 컬럼명(`study_id`)과 FK 제약은 그대로 유지 — 스키마 마이그레이션 없이 애플리케이션 코드
  레벨에서만 "다른 Bounded Context 엔티티를 내 Aggregate에 영속화하지 않는다"를 적용했다.
- 이 변경으로 `CalendarRepository.findByStudyIdIn`에 있던 N+1 방지용 `JOIN FETCH c.study` 쿼리가
  통째로 필요 없어졌다 — Calendar가 더 이상 Study를 로딩하지 않고, 호출부(`CalendarService`)가
  이미 자기 소속 스터디 목록을 `Map<Long, Study>`로 한 번에 들고 있기 때문. **결합을 없앴더니
  N+1 회피 코드도 같이 사라진 사례.**
- `CalendarEventResponse.from(...)` 단일 메서드를 `forPersonal(...)` / `forStudy(calendar, studyTitle,
  canManage)`로 분리해, DTO가 더 이상 Study 엔티티를 몰라도 되게 했다(제목/방장 여부는 호출부가
  StudyService로 조회해서 원시 타입으로 넘긴다).
- `CalendarService`는 Study 상세가 필요한 시점(등록/조회/수정/삭제/통합조회)마다
  `studyService.getStudyEntity(studyId)`로 **일시적으로만** 조회한다 — Calendar 엔티티 자체에는
  절대 저장하지 않는다.

## 4. 위반 시 자동으로 실패하는 테스트 추가

Spring Boot 4.1.0(최신 버전) 환경에서 Spring Modulith의 버전 호환이 검증되지 않아, 계획서가 제시한
대안인 **ArchUnit**을 채택했다 (`build.gradle`에 `com.tngtech.archunit:archunit-junit5:1.3.0` 추가).

`src/test/java/com/groovy/backend/architecture/`에 2개 테스트 클래스, 총 3개 규칙:

- `ModuleBoundaryTest`
  - `other_domains_must_not_access_user_repository_directly`: `domain.user` 밖의 어떤 클래스도
    `domain.user.repository` 패키지에 의존하면 실패
  - `other_domains_must_not_access_study_repositories_directly`: `domain.study` 밖의 어떤 클래스도
    `domain.study.repository` 패키지에 의존하면 실패
- `CalendarEntityBoundaryTest`
  - `calendarEntityMustNotHoldStudyEntityAsField`: `Calendar` 엔티티가 `Study` 타입 필드를 가지면 실패
    (리플렉션 기반 단순 검증)

**검증 결과 (최종, 실제 MySQL/Redis 대상)**: 루트 `docker-compose.yml`로 띄운 MySQL(groovy_db)·Redis
컨테이너를 대상으로 `./gradlew test` 전체 스위트를 실행해 **5개 테스트 전부 통과(실패 0, 에러 0)**했다.

| 테스트 | 결과 | 의미 |
|---|---|---|
| `GroovyApplicationTests.contextLoads` | ✅ | 전체 Spring 컨텍스트 기동 + Flyway 마이그레이션(V1~V8) 성공 — 이번에 새로 얽은 Service 간 의존성(UserService/StudyService/ApplicationService를 여러 도메인이 주입)에 순환 의존·누락 빈이 없음을 실제로 확인 |
| `ConcurrencyTest` | ✅ | 가상 스레드로 500개 동시 헬스체크 요청 처리, 리팩터링이 런타임 동작에 영향 없음 |
| `ModuleBoundaryTest` (2 규칙) | ✅ | cross-domain Repository 직접 접근 0건 |
| `CalendarEntityBoundaryTest` | ✅ | Calendar가 Study 엔티티를 필드로 갖지 않음 |

## 5. 완료 기준 체크

- [x] cross-domain repository/entity 직접 참조가 0건 — ArchUnit 규칙 3개로 CI에서 계속 검증됨
- [x] 도메인 간 통신이 전부 Application Event 또는 명시적 인터페이스(Service 공개 API)를 거침
- [x] 도메인 경계 재검토 완료 — 분리/통합 대상 없음, Tag 소유권만 Phase 2로 이관

## 6. 다음 단계로 넘길 것

- **Tag 소유권 확정**: `StudyTag`/`UserTag`를 어느 서비스가 갖고, `Tag` 마스터 데이터를 어떻게
  공유할지 Phase 2(서비스 경계 및 Contract 정의)에서 결정.
- **UserController의 조합 책임**: `/api/users/me/studies`, `/me/applications`를 Gateway BFF로
  옮길지 User 서비스가 Study 서비스를 내부 API로 호출할지도 Phase 2 안건.
- **엔티티 완전 분리는 아직**: Memoir/Notification 등은 여전히 `User`/`Study`를 JPA 연관관계로
  들고 있다(Calendar만 예외적으로 먼저 정리). DB가 실제로 나뉘는 Phase 7에서 나머지도 ID 참조로
  전환해야 한다 — 지금 전부 다 걷어내면 이번 Phase의 위험 대비 효율이 낮다고 판단해 범위에서 뺐다.
