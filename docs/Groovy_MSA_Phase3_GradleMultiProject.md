# Groovy MSA 전환 — Phase 3: Gradle Multi-Project 구조 전환

> 상위 계획: [`Groovy_MSA_전환계획.md`](./Groovy_MSA_전환계획.md) Phase 3
> 선행 문서: [`Groovy_MSA_Phase2_서비스경계와Contract.md`](./Groovy_MSA_Phase2_서비스경계와Contract.md)
> 목표: 모노레포는 유지하되, 서비스별로 독립 빌드/실행이 가능한 구조를 먼저 만든다.

## 1. 설계 결정 — 기존 `groovy/`는 건드리지 않는다

계획서의 디렉터리 예시에는 `backend/` 아래에 서비스들만 있고 기존 모놀리스가 보이지 않는다.
Phase 4(Compose 뼈대)와 Phase 5(Gateway)의 예시가 `legacy-monolith: build: ./groovy`로 **기존
`groovy/` 모듈을 그대로 유지**한다고 명시하므로, 이번 Phase에서는:

- 기존 `groovy/`(단일 모듈, 자체 `settings.gradle`/`gradlew`) — **완전히 그대로 둔다.** Phase 1/2에서
  검증한 도메인 로직·테스트·빌드가 이 구조에 있으므로 여기에 손대면 지금까지의 검증이 무의미해진다.
- 새 `backend/`(멀티모듈, 별도의 독립된 `settings.gradle`/`gradlew`) — **텅 빈 골격만** 만든다.
  실제 도메인 코드는 Phase 6부터 서비스가 하나씩 추출될 때(가장 먼저 Notification) `groovy/`에서
  이 골격 안으로 옮겨온다.

두 빌드는 서로 참조하지 않는 완전히 독립적인 Gradle 프로젝트다 — 하나가 깨져도 다른 하나에
영향이 없다. 이것도 일종의 Strangler Fig: 빌드 구조부터 먼저 "새 집"을 지어놓고, 코드는 나중에
방마다 옮긴다.

## 2. 실제로 만든 구조

```
backend/
├── gradlew, gradlew.bat, gradle/wrapper/   (groovy/와 동일한 Gradle 9.5.1 wrapper)
├── settings.gradle                          (8개 서브모듈 등록)
├── build.gradle                             (plugin 버전 선언 + allprojects 공통 설정)
├── libs/
│   ├── event-contract/                      (순수 DTO — Phase 2 Event Contract를 타입으로)
│   │   └── EventEnvelope, EventTypes, study/*, calendar/*, content/*, identity/* payload record 11종
│   └── observability/                       (공유 로깅 설정만)
│       └── logback-json.xml, LogFields
└── services/
    ├── api-gateway/           (port 8080)
    ├── identity-service/      (port 8081)
    ├── study-service/         (port 8082)
    ├── content-service/       (port 8083)
    ├── calendar-service/      (port 8084)
    └── notification-service/  (port 8085)
```

각 서비스는 `@SpringBootApplication` 하나 + 확인용 `ServiceInfoController`(`GET /`) +
`application.yml`(고유 포트) + `libs/observability`의 공유 로그 설정을 쓰는
`logback-spring.xml` + context-load 테스트 1개로 구성된 최소 골격이다. 아직 DB/JPA/Flyway는
연결하지 않았다 — 옮겨올 도메인 코드가 없는데 미리 연결해봐야 검증할 게 없기 때문.

### `common` 모듈 경고 준수 여부

계획서가 경고한 "Entity/Repository/Business Service를 common에 몰아넣는" 실수를 피했는지 점검:

| 모듈 | 담은 것 | 안 담은 것 |
|---|---|---|
| `libs/event-contract` | `record` DTO 11개, `EventEnvelope<T>`, `EventTypes` 상수 | Entity, Repository, 비즈니스 로직 — 0건 |
| `libs/observability` | 공유 `logback-json.xml`, `LogFields` 상수 | Entity, Repository, Business Service — 0건 |

두 모듈 다 `implementation`이 아니라 각 서비스가 명시적으로 골라서 의존한다(api-gateway는
`event-contract`를 아예 의존하지 않음 — 이벤트를 다루지 않는 서비스라서). "전부가 전부를
의존"하는 상태가 아님을 확인.

### Spring Cloud Gateway를 아직 넣지 않은 이유

`api-gateway` 모듈은 라우팅 로직이 없는 빈 껍데기다. Spring Boot 4.1.0(최신 버전)과 호환되는
Spring Cloud BOM 버전이 이 시점에 검증되지 않아, 실제 게이트웨이 의존성 추가는 Phase 5(API
Gateway 도입)에서 버전을 확인하며 진행하기로 미뤘다. 지금은 `spring-boot-starter-web`만으로
"포트 8080에서 뭔가 응답한다"는 골격만 갖췄다.

## 3. 검증 결과

```
cd backend && ./gradlew build
```

- **전체 빌드 성공**: 8개 서브모듈(`libs` 2개 + `services` 6개) 전부 컴파일·테스트 통과.
- **테스트 7/7 통과** (`event-contract`의 `EventEnvelopeTest` 1개 + 서비스별 context-load
  테스트 6개), 실패/에러 0건.
- **독립 실행 확인**: `./gradlew :services:notification-service:bootRun`으로 단독 기동 후
  `curl http://localhost:8085/` → `notification-service (Phase 3 골격...)` 응답,
  `curl http://localhost:8085/actuator/health` → `{"status":"UP"}` 확인. 계획서 완료 기준의
  예시 명령을 그대로 실행해 통과했다.
- 기존 `groovy/`(레거시 모놀리스)는 이번 Phase에서 파일 하나도 건드리지 않았다 — Phase 1/2에서
  검증한 상태 그대로 유지.

## 4. 완료 기준 체크

- [x] `settings.gradle`에 서비스별 모듈 등록 완료 (`libs:*` 2개, `services:*` 6개)
- [x] 각 서비스가 독립적으로 `./gradlew :services:notification-service:bootRun` 가능 (실제 실행해 확인)
- [x] `libs/event-contract`에 순수 DTO만 존재 (비즈니스 로직 없음)

## 5. 다음 단계로 넘길 것

- **api-gateway 라우팅 구현**: Phase 5에서 Spring Cloud Gateway(또는 Boot 4.1 호환이 안 되면
  대안 라우터)를 붙이고, 우선 모든 요청을 `legacy-monolith`(`groovy/`)로 흘려보내는 규칙부터
  추가한다.
- **Docker Compose 뼈대(Phase 4)**: 지금 만든 6개 서비스 + `legacy-monolith` + `mysql`/`redis`가
  한 네트워크에서 동시에 뜨는 compose 파일을 다음으로 작성한다.
- **실제 도메인 코드 이전은 아직**: 이 골격 서비스들은 Phase 6(Notification)부터 순서대로
  채워진다. 지금 비어있는 게 정상 상태다.
