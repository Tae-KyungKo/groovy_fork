# 07. notification-service의 legacy-monolith 기본값 정정

**날짜**: 2026-08-19
**분석 결과 원문 항목**: 종합 분석 🟠 7번(notification-service만 죽은 legacy-monolith를 기본값으로 가리킴)

## 배경

사용자가 "다른 3개 서비스도 같은 문제가 있냐"고 물어서 먼저 4개 서비스의 `application.yml`을
전부 재확인했다. **실제 버그(잘못된 기본 접속 주소)는 notification-service 하나뿐**이었다.

```
calendar/content/study-service: jwks-url 기본값 → identity-service:8081  (정상)
notification-service:           jwks-url 기본값 → legacy-monolith:8080  (죽은 호스트)
```

`docker-compose.local.yml`/`docker-compose.example.yml`은 4개 서비스 전부 `JWT_JWKS_URL`을
`identity-service:8081`로 override하고 있어 정상 배포 시엔 문제가 드러나지 않지만, compose
없이 서비스를 단독 기동(로컬 IDE 디버깅 등)하면 notification-service만 조용히 죽은 호스트를
가리키는 상태였다.

## 조치

`backend/services/notification-service/src/main/resources/application.yml`의 `jwt.jwks-url`
기본값을 `http://legacy-monolith:8080/...`에서 `http://identity-service:8081/...`로 수정.
바로 위 3줄의 주석도 "legacy-monolith의 공개키를 가져온다"/"legacy-monolith가 죽어 있는
동안"이라고 잘못된 설명을 하고 있어 함께 정정(study-service의 동일 위치 주석과 표현을 맞춤).

## 조치하지 않은 것 (다른 위치의 "legacy-monolith" 언급)

사용자 질문에 답하며 함께 확인한, 이번 항목 범위 밖의 주석 수준 언급들:

- `api-gateway/application.yml:10` — "legacy-monolith(groovy/)는 삭제됐다"는 과거형 서술이라
  문제 없음(그대로 둠).
- `study-service/application.yml:43` — "notification-service/legacy-monolith와 동일한
  패턴"이라는 비교 주석. 값 자체는 문제없지만 죽은 이름을 계속 언급함.
- `identity-service`의 `JwksController.java`/`TokenProvider.java`/`JwtKeyProvider.java`,
  `study-service`의 `TagPreferenceClientContractTest.java` — 전부 주석 수준.

이런 것들은 15번(주석 정리) 항목에서 한 번에 정리하기로 하고 이번엔 손대지 않았다.

## 검증

- `python3 -c "import yaml; yaml.safe_load(...)"`로 YAML 문법 확인.
- `./gradlew :services:notification-service:compileJava` 성공.
- `docker-compose.local.yml`/`docker-compose.example.yml`의 `JWT_JWKS_URL` 4곳 모두
  `identity-service:8081`로 이미 일치함을 재확인 — 이번 수정으로 compose 유무와 무관하게
  4개 서비스의 기본값이 전부 통일됨.
