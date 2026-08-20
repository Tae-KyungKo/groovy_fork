# 작업기록

Claude와 함께 groovy_fork 저장소에서 진행한 리팩터링/정리 작업을 시간순으로 기록한다.
발단은 2026-08-19 "백엔드+모니터링 MSA 전환 상태 종합 분석"이었고, 그 분석에서 나온 항목들을
사용자가 번호별로 지시한 순서대로 하나씩 처리한다. 각 항목은 이 디렉토리에 파일 하나로 남긴다.

파일명 규칙: `NN-짧은-슬러그.md` (NN은 분석 결과의 항목 번호, 처리 순서와 동일).

## 목록

| 번호 | 제목 | 상태 | 요약 |
|---|---|---|---|
| [00](./00-종합-분석.md) | 백엔드+모니터링 MSA 종합 분석 | 완료 | 전환완성도/결합도/보안/고아코드/설계결함 15개 항목 도출 |
| [01](./01-docker-compose-prod-제거.md) | docker-compose.prod.yml 제거 | 완료 | 운영 compose는 서버에서 별도 관리 — 저장소 사본 삭제 |
| [02](./02-원본-레포-통합-주의점.md) | 원본 레포 통합 시 주의점 문서화 | 완료 | Grafana 익명 Admin 등 통합 시 반드시 손볼 항목 정리 |
| [03](./03-event-contract-통합.md) | event-contract 실사용 전환 | 완료 | 4개 서비스의 중복 OutboxEnvelope/NotificationPayload를 공유 타입으로 교체 |
| [04](./04-kafka-dlq.md) | Kafka DLQ 추가 | 완료 | notification-service 컨슈머에 재시도+Dead Letter Topic 도입 |
| [05](./05-alert-rules-stub-수정.md) | alert.rules.yml 스텁 규칙 수정 | 완료 | `expr: 1` 스텁/합산 알림을 서비스별 실제 지표로 교체, promtool로 검증 |
| [06](./06-공유-인프라-라이브러리화.md) | 공유 인프라 라이브러리화 | 완료 | libs:web-common/libs:security-common 신설, 5개 서비스 복붙 코드 제거 |
| [07](./07-notification-service-jwks-url-정정.md) | notification-service jwks-url 정정 | 완료 | legacy-monolith 기본값 → identity-service로 수정 |
| [08](./08-동기호출-서킷브레이커-재시도.md) | 동기 호출 CircuitBreaker/Retry | 완료 | libs:client-common 신설(B안), UserServiceClient 중복도 함께 정리 |
| — | 13번(빈 디렉토리 정리) | 완료(부수적) | 08번 작업 중 빈 디렉토리 정리 명령이 대상을 함께 삭제 — 미추적/빈 디렉토리라 안전 |
| [09](./09-grafana-대시보드-스키마-수정.md) | Grafana 대시보드 스키마 수정 | 완료 | v2 스키마 → 실서빙 중인 classic 스키마로 교체(datasource uid 불일치는 오보로 판명) |
| [10](./10-서비스간-인증-심층방어.md) | 서비스 간 인증 심층방어 설명 | 완료(설명) | mTLS/내부 토큰 옵션 정리, 게이트웨이 와일드카드 라우팅 문제 추가 발견 |
| [11](./11-identity-service-서명키-인스턴스-문제.md) | JWT 서명키 인스턴스 문제 설명 | 완료(설명) | 수평 확장 시점에 손볼 기술부채로 확인, 해결 옵션 정리 |
| [12](./12-out-디렉토리-정리.md) | out/ 디렉토리 정리 | 완료 | git rm --cached + 로컬 삭제 |
| [14](./14-LogFields-죽은코드-원인분석.md) | LogFields.java 원인 분석 | 완료(설명) | traceId는 Micrometer가 우연히 채움, service는 애초에 채우는 코드가 없음 |
| [15](./15-주석-정리.md) | 주석 정리 | 완료 | 오해 소지 있는 3곳 정정, 정확한 과거형 서술은 보존 |

## 진행 대기 중인 항목
없음 — 2026-08-19 종합 분석에서 나온 15개 항목 전부 처리 완료.

## 15개 항목과 별개로 진행한 문서화 작업

| 날짜 | 제목 | 산출물 |
|---|---|---|
| 2026-08-19 | MSA DB 관계도 문서화 | [`../Groovy_MSA_DB_관계도.md`](../Groovy_MSA_DB_관계도.md) — 서비스별 DB/테이블, 서비스 간 참조 관계, 동기/비동기 통신 방법, 모니터링 DB 지표, Mermaid ERD/통신 흐름도. `mysqld-exporter`가 인스턴스 단위로만 수집되고 Grafana에는 DB 지표 패널이 없다는 것, `study_db.tags`가 identity_db의 실시간 미동기화 사본이라는 것을 재확인했다. |
