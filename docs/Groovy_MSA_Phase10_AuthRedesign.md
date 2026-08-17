# Groovy MSA 전환 — Phase 10: 인증 구조 재설계

> 상위 계획: [`Groovy_MSA_전환계획.md`](./Groovy_MSA_전환계획.md) Phase 10
> 선행 문서: [`Groovy_MSA_Phase9_MessageBrokerOutbox.md`](./Groovy_MSA_Phase9_MessageBrokerOutbox.md)
> 목표: 모든 서비스가 같은 HMAC 공유 시크릿(`JWT_SECRET_KEY`)을 쓰던 상태에서 벗어난다.

## 1. 범위 — identity-service가 아니라 legacy-monolith가 발급자

계획서 그림은 `identity-service(Private Key 서명) → Gateway → Resource Server(Public Key 검증)`
지만, identity-service는 아직 실제로 추출되지 않았다(User 도메인 코드가 여전히 legacy에 있음).
그래서 **legacy-monolith가 사실상의 발급자 역할을 계속 맡되, HMAC 대신 RSA로 서명하고 공개키를
JWKS로 공개**하는 방식으로 목표(시크릿 비공유)를 달성했다. identity-service가 실제로 추출되는
시점에 발급 책임(JwtKeyProvider/TokenProvider/JwksController)을 그 서비스로 그대로 옮기면 된다
— 지금 만든 구조 자체가 이미 "발급자 1곳, 검증자 여럿"이라 이관이 어렵지 않다.

## 2. JWKS 도입

### 2-1. legacy-monolith(발급자)

- `JwtKeyProvider`: 기동 시 RSA 2048비트 키 쌍을 생성. **단순화(문서화된 한계)**: 키가
  인스턴스 메모리에만 있어 재기동하면 바뀐다 — legacy-monolith가 지금 단일 인스턴스라
  가능한 단순화이고, 인스턴스를 2개 이상 띄우는 순간 깨진다(실제 배포라면 키를 파일/시크릿
  매니저에서 로드해 모든 인스턴스가 공유해야 함 — Key Rotation과 함께 다룰 별도 주제로 남김).
- `TokenProvider`: HMAC `SecretKey` → RSA `PrivateKey`로 서명(`Jwts.SIG.RS256`), 헤더에
  `kid`(키 지문에서 결정적으로 생성, `idFromThumbprint()`)를 싣는다.
- `JwksController`: `GET /.well-known/jwks.json` 신규, `SecurityConfig` permitAll 추가 —
  "공개"키이므로 인증 없이 완전히 열려 있어야 한다.
- `jwt.secret-key`(`JWT_SECRET_KEY`) 설정 완전히 제거.

### 2-2. notification-service(검증자)

- `JwksKeyLocator`: `legacy-monolith`의 JWKS를 fetch해 `kid → PublicKey`로 캐싱(TTL 5분,
  모르는 kid를 만나면 즉시 재조회 — 키 회전 대비).
- `TokenProvider`: HMAC 검증 → `Jwts.parser().keyLocator(jwksKeyLocator)`로 교체. 이 서비스는
  이제 **개인키를 전혀 모른다** — 검증만 할 수 있고 발급은 할 수 없다.
- `jwt.jwks-url`(`JWT_JWKS_URL`)로 legacy의 JWKS 주소를 가리킨다. `JWT_SECRET_KEY` 완전 제거.

### 2-3. 실제로 겪은 문제와 해결 (jjwt API는 전부 바이트코드로 사전 검증)

1. `JwkSet`/`Jwk`를 컨트롤러에서 그대로 반환하면 Boot 4.1의 Jackson이 `{"keys":{}}`(빈 객체)로
   잘못 직렬화했다. jjwt의 Map 구현이 Boot 4.1 Jackson과 안 맞는 것으로 보인다 — 순수
   `LinkedHashMap`/`List`로 명시적으로 한 번 더 감싸서 반환하도록 고쳐 해결했다.
2. jjwt 0.12.6의 JWKS 관련 API(`Jwks.builder()`, `Jwks.set()`, `Jwks.setParser()`,
   `RsaPublicJwk`, `Locator<Key>`, `ProtectedHeader.getKeyId()` 등)는 실제 jar를 javap로
   전부 확인한 뒤 구현했다 — 이번에도 추측 대신 바이트코드 확인 방식을 그대로 적용했다.

## 3. 서비스 간 채널(Kafka) 인증 — SASL/PLAIN

Phase 9에서 `/internal/notifications` HTTP API 자체가 Kafka로 대체됐기 때문에, "서비스 간
내부 API 호출 인증" 완료 기준은 이제 **Kafka 접근 자체를 인증으로 막는 것**으로 해석해
적용했다.

- 클라이언트 리스너를 `SASL_PLAINTEXT`로, 계정 `app`(legacy/notification-service 공용)만
  접속을 허용한다. 컨트롤러 리스너는 PLAINTEXT로 유지(단일 노드라 브로커 간 트래픽이
  사실상 없음, 지금 범위는 클라이언트 인증).
- legacy/notification-service 둘 다 `spring.kafka.security.protocol=SASL_PLAINTEXT` +
  `sasl.jaas.config`로 접속. 로컬(비Docker) 개발은 `PLAINTEXT` 기본값 유지 —
  `docker-compose.msa.yml`만 SASL로 덮어쓴다.

### 실제로 겪은 문제 2가지

1. **리스너 이름을 "SASL_PLAINTEXT"로 두면 안 됨**: 이미지의 `KAFKA_` 환경변수 → 설정 변환이
   언더스코어를 전부 점으로 바꾸는데, 리스너 이름 자체에 언더스코어가 들어 있으면
   `listener.name.sasl_plaintext...`가 `listener.name.sasl.plaintext...`로 잘못 쪼개진다.
   리스너 이름을 언더스코어 없는 `CLIENT`로 바꿔 해결했다(리스너 이름은 임의의 별칭이라
   클라이언트는 이 이름을 몰라도 된다 — 실제 보안 프로토콜만 `SASL_PLAINTEXT`로 매핑하면 됨).
2. **`configure` 스크립트가 `KAFKA_OPTS`를 하드 요구**: SASL 리스너가 감지되면 내용과 무관하게
   `KAFKA_OPTS`가 "설정되어 있기만" 해도 되는데(`ensure()` 함수가 `${!1}`로 unset 여부만
   확인, `set -u`라 unset이면 즉시 죽음), 우리는 env-var 기반 JAAS 설정 방식을 쓰므로
   `KAFKA_OPTS`를 안 썼다가 컨테이너가 기동 직후 죽었다. 아무 값이나(`-Dfile.encoding=UTF-8`)
   채워 해결 — 관련 경고 메시지는 무해하다.

## 4. 실제 검증 (Docker Compose, mock 없이 실측)

### 4-1. JWKS 기반 인증 전체 흐름
```
1. GET /.well-known/jwks.json → 인증 없이 200, 올바른 JWK(kty=RSA, n, e, kid) 응답 확인
2. 로그인 → JWT 헤더 디코드 → {"kid":"...","alg":"RS256"} 확인 (더 이상 HS256 아님)
3. 신청 → Outbox → Kafka → notification-service 소비 → 알림 생성
4. GET /api/notifications (RS256 토큰으로) → notification-service 로그: "JWKS 갱신 완료: kid=[...]"
   → 200, 알림 정상 조회. legacy와 notification-service가 시크릿을 한 번도 주고받지 않고
   공개키만으로 신뢰 관계가 성립함을 확인.
```

### 4-2. Kafka 인증 (3가지 케이스 실측)
```
자격증명 없이 접속 시도  → 핸드셰이크가 응답 없이 멎음(30초 타임아웃으로 강제 종료)
잘못된 비밀번호로 접속   → 즉시 거부: SaslAuthenticationException: Invalid username or password
올바른 자격증명으로 접속 → 성공, 토픽 목록(notification-events 등) 정상 조회
```

## 5. 완료 기준 체크

- [x] Gateway 또는 각 서비스가 HMAC 공유 secret이 아닌 JWKS 기반으로 검증 — §4-1 실측
- [x] 서비스 간 내부 API 호출에 별도 인증 메커니즘 존재 (누구나 호출 가능한 상태 아님) —
      §4-2 실측(Kafka SASL/PLAIN)

## 6. 다음 단계로 넘길 것

- **Key Rotation은 범위 밖**: `idFromThumbprint()`로 kid를 결정적으로 만들어 다중 키 공존
  구조와는 호환되게 해뒀지만, 실제로 "이전 키를 한동안 같이 공개하며 교체"하는 로직은
  구현하지 않았다.
- **Kafka는 공용 계정 "app" 하나**: legacy(프로듀서)와 notification-service(컨슈머)가 같은
  계정을 쓴다. 서비스별로 계정을 나누고 ACL로 "이 계정은 이 토픽에 쓰기만/읽기만 가능"까지
  세분화하는 건 Phase 11(Resilience)/그 이후로 남긴다.
- **identity-service 실제 추출 시**: `JwtKeyProvider`/`TokenProvider`/`JwksController`를
  그대로 그 서비스로 옮기면 된다 — 지금 구조가 이미 "발급자 1곳 vs 검증자 여럿" 모양이라
  이관 비용이 낮다.
