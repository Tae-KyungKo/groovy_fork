# Groovy MSA 구조 개요 및 실행 방법

> 상세 설계/검증 근거는 [`Groovy_MSA_전환계획.md`](./Groovy_MSA_전환계획.md)와 각 Phase 문서
> (`Groovy_MSA_Phase0_의존성분석.md` ~ `Groovy_MSA_Phase13_테스트전략확장.md`) 참고. 이 문서는
> "지금 뭐가 어떻게 떠 있는지"만 간단히 정리한다.

## 1. 두 개의 스택

이 저장소는 두 개의 독립적인 Docker Compose 스택을 갖고 있고, 포트/네트워크/컨테이너 이름이
겹치지 않아 동시에 띄워도 서로 충돌하지 않는다.

| | 파일 | 용도 | 포트대 |
|---|---|---|---|
| 모놀리스(운영) | `docker-compose.yml` | 원래의 단일 백엔드+프론트엔드 로컬 실행 | 표준(8080, 3306, 6379, 5173...) |
| MSA 검증 스택 | `docker-compose.msa.yml` | Strangler Fig로 추출 중인 MSA 구조 검증용 | 18xxx |

`docker-compose.msa.yml`은 검증 목적의 별도 스택이라 포트를 18xxx대로 옮겼다(Phase 4).

## 2. MSA 스택 실행

```bash
docker compose -f docker-compose.msa.yml up -d --build
docker compose -f docker-compose.msa.yml ps
docker compose -f docker-compose.msa.yml down
```

## 3. 뜨는 서비스 목록

### 애플리케이션

| 서비스 | 역할 | 호스트 포트 | 상태 |
|---|---|---|---|
| `api-gateway` | 모든 요청의 진입점(Spring Cloud Gateway MVC). 경로별로 legacy-monolith 또는 notification-service로 라우팅 | 18080 (mgmt 18090) | 실제 라우팅 동작 |
| `legacy-monolith` | 아직 추출되지 않은 나머지 도메인(User/Study/Memoir/Calendar/Tag) 전부를 서빙하는 기존 모놀리스(Strangler Fig) | 8086→8080 | 운영 중 |
| `notification-service` | 첫 번째로 추출된 서비스. 알림 저장 + SSE 푸시(Redis pub/sub) + Kafka 이벤트 소비 | 18085 | 실제 도메인 로직 동작 |
| `identity-service` | User(+인증) 도메인이 옮겨올 자리 | 18081 | Phase 3 빈 골격 |
| `study-service` | Study/Application/Waitlist/Tag 도메인이 옮겨올 자리 | 18082 | Phase 3 빈 골격 |
| `content-service` | Memoir(+댓글/좋아요) 도메인이 옮겨올 자리 | 18083 | Phase 3 빈 골격 |
| `calendar-service` | Calendar 도메인이 옮겨올 자리 | 18084 | Phase 3 빈 골격 |

### 인프라

| 서비스 | 역할 | 비고 |
|---|---|---|
| `msa-mysql` | 컨테이너 1개 안에서 스키마 소유권만 분리(`groovy_db`=legacy, `notification_db`=notification-service 전용 계정) | 18306→3306 (Phase 13에서 테스트용으로 호스트 노출) |
| `msa-redis` | notification-service의 SSE 인스턴스 간 pub/sub + 구독 티켓 저장 | legacy는 더 이상 Redis 안 씀(Phase 6) |
| `kafka` | 단일 노드 KRaft 모드. legacy의 Outbox → notification-service 소비(SASL/PLAIN 인증) | Phase 9~10 |

### 관측(Observability)

| 서비스 | 역할 | 호스트 포트 |
|---|---|---|
| `tempo` | 분산 트레이싱 백엔드(OTLP 수신) | 18200 |
| `loki` | 로그 저장/검색 (기존 `monitoring/` 설정 재사용) | 18310 |
| `alloy` | Docker 컨테이너 stdout을 자동 수집해 Loki로 전송 | - |
| `grafana` | Tempo+Loki 데이터소스로 트레이스/로그 조회 | 18300 |

## 4. 요청 흐름 예시

```
브라우저/클라이언트
   │
   ▼
api-gateway :18080
   ├─ Path=/api/notifications/**  →  notification-service :8085
   └─ Path=/**(나머지 전부)        →  legacy-monolith :8080
```

Study 참여 신청처럼 알림을 발생시키는 동작은 legacy-monolith가 처리 후 같은 트랜잭션 안에서
`outbox_events`에 기록하고(Transactional Outbox), 별도 스케줄러가 Kafka로 발행하면
notification-service가 소비해 알림을 저장+SSE 푸시한다(At-least-once + Inbox로 멱등 처리).

인증은 legacy-monolith가 RSA 키로 서명하고(`/.well-known/jwks.json`으로 공개키 공개),
notification-service는 이 공개키만 가져다 서명을 검증한다 — 시크릿을 공유하지 않는다.

## 5. 아직 안 된 것

- identity/study/content/calendar-service 4곳은 여전히 Phase 3의 빈 골격 그대로다. 실제
  도메인 로직은 legacy-monolith에 남아 있다 — Strangler Fig 패턴으로 다음 서비스 추출은
  notification-service 때와 같은 절차(Phase 6~11 반복)로 진행하면 된다.
- 원본 `docker-compose.yml`/`docker-compose.prod.yml`은 이 MSA 작업 동안 건드리지 않았다.
