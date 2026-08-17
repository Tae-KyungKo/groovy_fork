# Groovy MSA 전환 — Phase 8: 서비스 간 동기 통신 구축

> 상위 계획: [`Groovy_MSA_전환계획.md`](./Groovy_MSA_전환계획.md) Phase 8
> 선행 문서: [`Groovy_MSA_Phase7_DatabasePerService.md`](./Groovy_MSA_Phase7_DatabasePerService.md)
> 목표: "즉시 응답이 필요한" 서비스 간 통신을 REST로 구축하고, 반드시 타임아웃을 명시한다.

## 1. 범위 — 새 호출을 만들지 않고 기존 호출을 완성했다

계획서 예시는 "content-service가 study-service에 멤버십을 조회"처럼 **아직 존재하지 않는
두 서비스** 사이의 조회성 호출이다. 지금 실제로 별도 프로세스로 추출된 서비스는
notification-service 하나뿐이라(Phase 7과 동일한 제약), 이 시점에 새로운 서비스 간 조회
호출을 추가하면 호출할 진짜 서비스가 없어 속 빈 실습 코드가 된다.

대신 Phase 6에서 이미 만든 **legacy-monolith → notification-service**
(`POST /internal/notifications`) 호출이 정확히 "서비스 간 동기 통신"이다 — RestClient로
호출하고 응답을 기다리는(블로킹) 진짜 네트워크 호출이라는 점에서 계획서가 다루는 대상과
기술적으로 동일하다. 이번 Phase는 이 호출에 빠져 있던 것, 즉 **타임아웃**을 채워 완성했다.

## 2. 타임아웃 설정

Boot 4.1(Spring Framework 7)에는 Boot 3.4~4.0에서 쓰던 `ClientHttpRequestFactorySettings`/
`ClientHttpRequestFactoryBuilder` 헬퍼가 없다(바이트코드로 직접 확인 — 클래스 자체가
`spring-boot`/`spring-web` 어디에도 없다). 대신 `spring-web`의 `JdkClientHttpRequestFactory`를
`java.net.http.HttpClient`(connect timeout 설정) 위에 얹고 `setReadTimeout`으로 읽기 타임아웃을
지정하는 방식을 썼다 — 이 역시 클래스 존재 여부와 시그니처를 javap로 실제 확인한 뒤 적용했다.

```java
HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
    .build();
JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
```

`NotificationClient`(`groovy/global/notification/`)에 적용, 기본값은 connect 2000ms /
read 3000ms, `application.yml`(`notification-service.connect-timeout-ms`,
`notification-service.read-timeout-ms`)에서 환경변수로 조정 가능하게 뒀다.

## 3. 실측 검증 — 실제로 타임아웃이 걸리는지 측정

기존에 떠 있는 실서비스(`groovy-mysql` 등 영구 볼륨을 쓰는 컨테이너)는 절대 건드리지 않고,
`docker-compose.msa.yml` 검증 스택 안에서만 실험했다.

1. **정상 흐름 회귀 확인**: 새 타임아웃 설정을 넣은 뒤에도 신청→알림 생성→조회 흐름이
   그대로 동작하는지 먼저 확인(회귀 없음).
2. **read-timeout이 실제로 발동하는지 측정**: 같은 Docker 네트워크 안에 "연결은 받아주지만
   응답은 절대 안 주는" 순수 블랙홀 리스너(Python 소켓, `accept()` 후 무한 대기)를 띄우고,
   `NOTIFICATION_SERVICE_URL`을 거기로 잠깐 돌린 뒤 알림 생성을 트리거해서 에러 로그가
   찍히기까지 걸린 시간을 쟀다.

   ```
   설정값: NOTIFICATION_SERVICE_READ_TIMEOUT_MS=2000
   실측값: 요청 후 약 2.099초에 ResourceAccessException 발생 (read timeout)
   ```

   첫 시도는 busybox `nc -lk`로 블랙홀을 만들었는데 0.557초 만에 실패했다 — 로그를 까보니
   원인이 `EOFException`(상대가 연결을 끊음)이었다. busybox nc가 연결을 받은 뒤 곧바로 닫아버려
   "응답 없이 계속 대기"를 재현하지 못한 것이었다(타임아웃이 아니라 상대가 끊어서 빨리 실패한
   것). 이 원인을 로그로 확인한 뒤, `accept()` 후 무한 대기하는 순수 Python 소켓으로 블랙홀을
   다시 만들었고, 그제서야 설정값(2000ms)에 정확히 근접한 2099ms에 실패했다 — **타임아웃을
   실측하려던 첫 시도 자체가 잘못된 실험이었음을 로그로 확인하고 바로잡은 사례**라 그대로
   기록해둔다.
3. 실험이 끝난 뒤 블랙홀 컨테이너를 제거하고 설정을 원래대로 되돌려 다시 정상 흐름을
   확인했다 — 실제 notification-service로 알림이 정상 도착.

## 4. 완료 기준 체크

- [x] Phase 2에서 정의한 Internal API 1~2개가 실제로 서비스 간 호출됨 —
      `POST /internal/notifications` (Phase 6에서 이미 구현, 이번에 완성)
- [x] Timeout 설정 존재 (기본값 방치 금지) — connect/read 둘 다 명시, 실제 발동 시간까지 실측

## 5. 다음 단계로 넘길 것

- **조회성(GET) Internal API**: `GET /internal/studies/{id}/membership/{userId}` 같은 계획서
  예시 호출은, 그 호출의 실제 당사자(study-service, content-service 등)가 다음에 추출될 때
  자연스럽게 생긴다. Phase 6/7에서 정립한 "도메인 추출 시 4단계 절차"에 이 Phase의 타임아웃
  패턴(RestClient + JdkClientHttpRequestFactory)을 그대로 재사용하면 된다.
- **Retry/Circuit Breaker는 아직**: 지금은 실패 시 로그만 남기고 삼킨다(Phase 6 설계 그대로).
  Retry+Backoff와 Circuit Breaker는 계획서 Phase 11(Resilience)의 몫 — 이번에 넣은 타임아웃이
  그 전제조건이었다("Timeout 없이 Retry만 걸면 Retry Storm").
