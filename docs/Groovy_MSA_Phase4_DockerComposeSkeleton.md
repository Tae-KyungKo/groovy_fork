# Groovy MSA 전환 — Phase 4: Docker Compose 뼈대 구축

> 상위 계획: [`Groovy_MSA_전환계획.md`](./Groovy_MSA_전환계획.md) Phase 4
> 선행 문서: [`Groovy_MSA_Phase3_GradleMultiProject.md`](./Groovy_MSA_Phase3_GradleMultiProject.md)
> 목표: 서비스를 실제로 코드에서 분리하기 전에, Compose 환경에서 여러 컨테이너가 이름 기반으로
> 통신할 수 있는 기반부터 만든다.

## 1. 만든 것

- **`backend/services/*/Dockerfile`** 6개(신규). 전부 동일한 멀티스테이지 패턴(JDK 빌드 → JRE
  런타임, 비root 유저, `/actuator/health` 헬스체크)이며 `groovy/Dockerfile`과 구조를 맞췄다.
  빌드 컨텍스트는 반드시 `backend/`(각 서비스 디렉터리가 아니라 루트)여야 한다 — Gradle
  멀티모듈이라 `libs/event-contract`, `libs/observability`를 함께 봐야
  `:services:<name>:bootJar`가 성립하기 때문.
- **`docker-compose.msa.yml`**(루트, 신규). 기존 `docker-compose.yml`/`docker-compose.prod.yml`은
  건드리지 않고 별도 파일로 분리했다 — 지금 있는 개발/운영 흐름을 조금도 흔들지 않으면서, MSA
  골격만 독립적으로 띄우고 검증할 수 있게 하기 위함.

### 왜 포트를 전부 18xxx로 옮겼나

기존 전체 스택(`docker-compose.yml`)이 이미 8080(backend)/3307(mysql)/6379(redis) 등을 쓰고
있어, 같은 시스템에서 두 스택을 동시에 띄워도 충돌하지 않도록 이 골격 스택의 호스트 포트를
전부 18080~18085(+legacy-monolith 8086)로 옮겼다. 최종 목표(게이트웨이가 8080을 받고 나머지는
내부 전용)는 Phase 5에서 기존 `docker-compose.yml`을 대체할 때 정리한다 — 지금은 "컨테이너가
서로 이름으로 찾을 수 있는가"만 검증하는 단계이므로 호스트 포트 번호 자체는 중요하지 않다.

### 컴포즈 안의 mysql/redis도 별도로 새로 띄움

`legacy-monolith`가 실제로 Flyway/JPA/Redis에 붙어야 완전히 기동하는지까지 확인하려면 진짜
DB가 필요했다. 기존에 떠 있는 `groovy-mysql`/`groovy-redis`(전체 스택)를 재사용하지 않고
`msa-mysql`/`msa-redis`라는 이름으로 이 compose 프로젝트 안에 새로 띄운 이유는, 두 스택이
서로 다른 Docker Compose 프로젝트(네트워크)에 속해 있어 기본적으로 서로의 컨테이너를 이름으로
찾을 수 없기 때문 — Phase 4가 검증하려는 "서비스명이 DNS 호스트명이 된다"는 정확히 **같은
네트워크 안에서만** 성립하는 성질이라, 검증 대상 컨테이너들을 전부 한 compose 파일(=한 네트워크)
안에 두는 게 맞다.

## 2. 검증 결과

```
docker compose -f docker-compose.msa.yml up -d --build
docker compose -f docker-compose.msa.yml ps
```

**9개 컨테이너 전부 기동 후 healthy**: `msa-mysql`, `msa-redis`, `legacy-monolith`(레거시
모놀리스), `api-gateway`, `identity-service`, `study-service`, `content-service`,
`calendar-service`, `notification-service`.

**컨테이너 간 이름 기반 통신** (`docker exec`로 확인):

```
$ docker exec groovy-msa-api-gateway wget -qO- http://notification-service:8085/
notification-service (Phase 3 골격 — Phase 6에서 첫 번째로 추출 예정)

$ docker exec groovy-msa-api-gateway wget -qO- http://identity-service:8081/
identity-service (Phase 3 골격 — 아직 도메인 로직 없음)

$ docker exec groovy-msa-notification-service wget -qO- http://study-service:8082/
study-service (Phase 3 골격 — 아직 도메인 로직 없음)

$ docker exec groovy-msa-calendar-service wget -qO- http://content-service:8083/
content-service (Phase 3 골격 — 아직 도메인 로직 없음)

$ docker exec groovy-msa-api-gateway wget -qO- http://legacy-monolith:8080/api/health
{"status":"UP","message":"Groovy Backend Phase 1 Active","data":null}
```

`legacy-monolith`가 `msa-mysql`(Flyway 마이그레이션 V1~V8 포함)과 `msa-redis`에 정상 접속해
healthy 상태가 된 것도 확인 — 계획서 원안 예시(`http://notification-service:8081` 형태)와
동일한 방식으로 서비스명이 곧 DNS 호스트명이 됨을 실제로 검증했다.

**검증 후 정리**: `docker compose -f docker-compose.msa.yml down`으로 이 스택만 내리고, 기존에
떠 있던 전체 스택(`groovy-backend`, `groovy-mysql` 등)은 전혀 영향받지 않고 계속 `healthy`
상태임을 확인했다 — 두 스택이 완전히 독립적으로 공존 가능함을 재확인.

## 3. 완료 기준 체크

- [x] `docker-compose up` 시 레거시 모놀리스 + 신규 빈 서비스 컨테이너가 동시에 뜸
- [x] 컨테이너 간 이름 기반 통신 확인 (`docker exec`로 curl/wget 테스트)

## 4. 다음 단계로 넘길 것

- **Phase 5(API Gateway 우선 도입)**: `api-gateway` 서비스에 실제 라우팅 규칙을 추가한다. 처음엔
  전부 `legacy-monolith`로 보내고(기능 변화 없음), 이후 서비스가 추출될 때마다 경로별로
  분기한다. Spring Boot 4.1과 호환되는 Spring Cloud Gateway BOM 버전을 이 시점에 확인해야 한다.
- **포트/네트워크 정리**: 이번 골격 스택은 검증용으로 18xxx 포트를 썼다. Phase 5에서
  `docker-compose.msa.yml`을 기존 `docker-compose.yml`과 통합하거나 대체하면서 최종 포트
  체계(게이트웨이만 외부 노출)로 정리한다.
- **DB는 아직 단일**: `msa-mysql` 하나를 legacy-monolith만 쓰고 있다. 서비스별 스키마 분리는
  계획서 Phase 7의 몫 — 지금은 "Phase 6까지는 단일 DB 유지" 원칙 그대로.
