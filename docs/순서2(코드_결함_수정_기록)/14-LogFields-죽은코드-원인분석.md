# 14. LogFields.java 죽은 코드 발생 원인 분석

**날짜**: 2026-08-19
**분석 결과 원문 항목**: 종합 분석 🟡 (LogFields.java의 TRACE_ID/SERVICE_NAME이 참조되지 않는 죽은 코드)
**성격**: 원인 분석 요청 항목 — 코드 변경 없이 문서로만 정리한다.

## 코드 재확인

```java
// backend/libs/observability/.../LogFields.java
public final class LogFields {
    public static final String TRACE_ID = "traceId";
    public static final String SERVICE_NAME = "service";
}
```

6개 서비스 전체에서 `LogFields.TRACE_ID`/`LogFields.SERVICE_NAME`를 import하거나 참조하는
코드는 0건이다.

## 실제로 로그 필드가 채워지는 경로를 추적해보니

- `traceId`는 각 서비스의 `TracingConfig.java`가 등록하는 `Slf4JEventListener`
  (`io.micrometer.tracing.otel.bridge.Slf4JEventListener`, Micrometer Tracing 라이브러리
  내부 클래스)가 span 시작/종료 시 **자기 내부에 하드코딩된 문자열** `"traceId"`/`"spanId"`로
  MDC에 직접 써넣는다. 애플리케이션 코드가 이 키 이름을 주입하거나 설정할 방법 자체가 없다.
- 최종 JSON 직렬화는 `observability/logback-json.xml`의 `LogstashEncoder`가 담당하는데,
  이 인코더는 **MDC에 있는 모든 키를 자동으로** JSON 필드로 펼쳐 넣는 라이브러리 기본
  동작을 그대로 쓴다 — 특정 키 이름을 알아야 동작하는 게 아니다.
- `service`는 이야기가 다르다: 이 값을 MDC에 실제로 넣는 코드(`MDC.put("service", ...)`류)가
  6개 서비스 전체에서 **0건**이다. 즉 `SERVICE_NAME` 상수가 가리키는 필드는 지금 JSON 로그
  출력에 아예 나타나지 않는다(traceId처럼 "우연히 값은 맞는데 안 쓰인다"가 아니라, "채워주는
  코드 자체가 없어서 필드가 존재하지 않는다"는 더 근본적인 상태).

## 왜 이런 죽은 코드가 생겼는가 (원인)

1. **"상수를 정의하는 것"과 "그 상수를 실제 코드 경로에 연결하는 것"이 분리된 두 작업인데,
   후자가 빠졌다.** 관측성 설정을 설계할 때 "이런 필드가 필요하다"를 먼저 상수로
   문서화해두고, 실제로 그 값을 채워 넣는 배선(라이브러리 설정, MDC.put 호출)은 나중
   작업으로 미루다가 잊힌 전형적인 패턴이다.
2. **traceId 쪽은 우연히 동작해서 아무도 눈치채지 못했다.** Micrometer가 알아서 같은
   문자열(`"traceId"`)로 MDC를 채워주기 때문에, 로그를 열어보면 `traceId` 필드가 정상적으로
   보인다 — `LogFields.TRACE_ID`를 실제로 쓰든 안 쓰든 결과가 똑같아서, "이 상수가 죽어있다"는
   신호가 겉으로 드러나지 않는다. 반대로 `SERVICE_NAME`은 아무것도 채워주는 게 없으니
   필드 자체가 로그에 안 보였을 텐데도, 그 사실이 지금까지 지적되지 않았다는 건 아무도 이
   필드로 실제 로그를 검색/필터링해보지 않았다는 뜻이기도 하다.
3. **주석("동일한 키를 쓰므로 매핑 불필요")이 절반만 맞는 설명이라 문제를 가렸다.** traceId에
   대해서는 "이미 라이브러리가 같은 키로 채워주니 이 상수를 실제로 연결할 필요 없다"는
   설명이 우연히 성립하지만, service는 애초에 아무도 채우지 않는 필드를 전제로 한 것이라
   같은 주석이 잘못된 안도감을 줬을 가능성이 크다.

## 정리하려면 (참고 — 실행은 하지 않음)

- `TRACE_ID`: 실제로 이 상수를 참조할 코드가 없어도 되는 상태(Micrometer가 대신 채움)이므로,
  상수를 지우거나 "이 상수는 참고 문서일 뿐 실제 코드 경로와 연결돼 있지 않다"고 주석을
  명확히 하는 것 중 하나가 필요하다.
- `SERVICE_NAME`: 실제로 값이 채워지지 않는 필드다. 정말 로그에 서비스명이 필요하면
  `logback-json.xml`의 `LogstashEncoder`에 `<customFields>{"service":"${spring.application.name}"}</customFields>`를
  추가해 실제로 채우거나, 필요 없으면 상수 자체를 삭제해야 한다.

원인 분석과 정리 방향까지는 정리했지만, "지금 지울지 / 실제로 연결할지"는 로그 필드
설계에 대한 판단이 필요해 지시 없이 코드를 바꾸지 않았습니다.
