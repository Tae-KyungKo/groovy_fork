# Groovy MSA 전환 — Phase 5: API Gateway 우선 도입 (Strangler Fig)

> 상위 계획: [`Groovy_MSA_전환계획.md`](Groovy_MSA_전환계획.md) Phase 5
> 선행 문서: [`Groovy_MSA_Phase4_DockerComposeSkeleton.md`](Groovy_MSA_Phase4_DockerComposeSkeleton.md)
> 목표: 클라이언트가 레거시/신규 서비스 구분 없이 하나의 진입점만 보게 한다. 이 Phase에서는
> 계획서의 "1단계"만 진행한다 — 모든 요청을 레거시 모놀리스로 그대로 보낸다(기능 변화 없음,
> 구조만 추가). 경로별로 신규 서비스에 나눠 보내는 "2단계"는 실제로 서비스가 추출되는
> Phase 6부터다(아직 notification-service 등은 빈 골격이라 보낼 데가 없다).

## 1. 핵심 리스크였던 버전 호환 문제 — 실제로 겪고 해결함

착수 전 예상했던 대로, Spring Cloud와 Spring Boot 4.1.0(이 프로젝트가 쓰는 최신 버전) 간 버전
호환이 문제였다. 추측 대신 Maven Central 메타데이터로 직접 확인했다.

- Spring Cloud 최신 안정 버전(2025.1.2)의 POM을 보면 `spring-boot.version=4.0.7`로 고정돼 있다
  — 우리 프로젝트가 쓰는 4.1.0보다 한 마이너 버전 낮다. 그 시점에 Boot 4.1.0은 막 나온
  최신 릴리스라 Spring Cloud가 아직 못 따라온 상태.
- **해결**: `api-gateway` 서비스만 예외적으로 Spring Boot **4.0.7**에 고정한다. 나머지 5개
  서비스는 계속 4.1.0을 쓴다 — Spring Cloud에 의존하지 않으니 문제 없다. 이건 마이크로서비스가
  누리는 실제 이점이기도 하다: 서비스마다 독립적인 배포/버전 트레인을 가질 수 있다.
- **Gradle에서 실제로 부딪힌 문제**: `backend/build.gradle` 루트에서
  `id 'org.springframework.boot' version '4.1.0' apply false`로 한 번 선언해두고 하위
  프로젝트가 버전 없이 `id 'org.springframework.boot'`만 쓰는 기존 패턴(Phase 3)에서는, 같은
  빌드 안에서 같은 플러그인 id에 다른 버전을 요청하는 게 Gradle 자체에서 막힌다
  (`Error resolving plugin ... already on the classpath with a different version`).
  → 루트의 버전 선언을 없애고, **6개 서비스 전부가 자기 build.gradle에 자기 Boot 버전을
  직접 명시**하도록 바꿨다(5개는 4.1.0, api-gateway만 4.0.7).
- **스택 선택**: 반응형(WebFlux) `spring-cloud-starter-gateway` 대신, 서블릿(MVC) 기반
  `spring-cloud-starter-gateway-server-webmvc`를 썼다. groovy 레거시와 나머지 5개 서비스가
  전부 서블릿 스택(WebFlux 아님)이라 게이트웨이만 반응형으로 가면 스택이 갈라진다.

## 2. 라우팅 설정

`backend/services/api-gateway/src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: legacy-monolith
              uri: http://legacy-monolith:8080
              predicates:
                - Path=/**

management:
  server:
    port: 8090   # 아래 §3 참고
```

Java 설정 클래스 없이 YAML 프로퍼티만으로 구성했다 — `spring-cloud-gateway-server-webmvc`의
`spring-configuration-metadata.json`을 직접 열어 프로퍼티 경로(`spring.cloud.gateway.server.
webmvc.routes`)와 `RouteProperties`/`PredicateProperties`의 실제 필드를 확인한 뒤 작성했다
(추측 아님).

## 3. actuator를 별도 관리 포트로 분리한 이유

`Path=/**`가 정말 "전부"를 의미하므로, 그대로 두면 게이트웨이 자신의 `/actuator/health`
요청까지 레거시 모놀리스로 흘러간다. 그러면 게이트웨이 프로세스 자체가 죽어도(라우팅 로직이
멎어도) 레거시만 살아있으면 Docker 헬스체크가 "건강함"으로 잘못 보고하게 된다. 이걸
`management.server.port: 8090`으로 actuator를 메인 서버 포트(8080, 라우팅 대상)와 분리해서
막았다 — 흔한 실무 패턴이고, Gateway가 아니라도 "프록시 뒤의 서비스가 자기 헬스체크 경로까지
프록시당하는" 문제는 자주 나온다.

## 4. 검증 결과

```
docker compose -f docker-compose.msa.yml up -d --build
```

- `api-gateway`, `legacy-monolith` 포함 9개 컨테이너 전부 healthy.
- **게이트웨이 자체 헬스(관리 포트, 프록시 우회)**:
  `curl http://localhost:18090/actuator/health` → `{"status":"UP", ...}` (게이트웨이 자신의 상태)
- **메인 포트(8080)를 통한 프록시 동작 확인**:
  `curl http://localhost:18080/api/health` → `{"status":"UP","message":"Groovy Backend Phase 1 Active",...}`
  — legacy-monolith를 **직접** 호출한 결과(`curl http://localhost:8086/api/health`)와 **완전히 동일**.
- **임의 경로도 전부 넘어가는지**(`Path=/**` 확인): `/api/studies`를 게이트웨이 경유/직접 호출
  둘 다 동일한 상태 코드(200) 확인 — `/api/health` 하나만 우연히 맞은 게 아님을 확인.
- Phase 4에서 검증한 서비스 간 이름 기반 통신(`notification-service` → `study-service` 등)도
  회귀 없이 그대로 동작.
- 검증 후 `down`으로 정리, 기존에 떠 있던 전체 스택은 영향 없음(계속 healthy).

## 5. 완료 기준 체크

- [x] Gateway가 떠 있고, 모든 요청이 일단 레거시 모놀리스로 라우팅됨 (기능 변화 없음, 구조만 추가)
- [ ] nginx는 Gateway 앞단의 SSL 종료/정적 파일 서빙 역할로 재정의 — **보류**. 현재 로컬
      통합 개발(`docker-compose.yml`)에는 nginx가 없고, `docker-compose.prod.yml`(운영)에만
      nginx + certbot이 있다. 이 골격은 아직 운영 배포용이 아니라 로컬 검증용이라 nginx 재정의는
      실제로 `docker-compose.msa.yml`이 운영 compose를 대체/통합하는 시점(레거시 monolith 완전
      제거에 가까워질 때)에 함께 처리하는 게 맞다고 판단해 지금은 다루지 않았다.

## 6. 다음 단계로 넘길 것

- **Phase 6(Notification 첫 추출)**: notification-service에 실제 도메인 코드가 들어오는 순간,
  게이트웨이 라우트 목록에 `/api/notifications/**` → `notification-service:8085`를 legacy 규칙보다
  **위에** 추가한다(YAML list 순서 = 매칭 우선순위이므로, 구체적인 규칙이 `Path=/**`보다 먼저
  와야 한다).
- **인증(JWT) 검증을 게이트웨이로 옮기는 것**은 계획서가 Phase 5에서 시작한다고 했지만, 지금은
  아직 하지 않았다 — legacy-monolith가 이미 자체적으로 JWT를 검증하고 있어(Spring Security),
  게이트웨이에서 이중으로 검증 로직을 추가하면 지금 당장은 득보다 실이 크다(두 곳에서 시크릿
  관리, 검증 로직 중복). Phase 10(인증 구조 재설계, JWKS 기반)에서 identity-service가 실제로
  분리된 뒤 본격적으로 옮긴다.
- **nginx 역할 재정의**: 위 §5 참고, Phase 9~11 근처 실제 배포 전환 시점에 처리.
