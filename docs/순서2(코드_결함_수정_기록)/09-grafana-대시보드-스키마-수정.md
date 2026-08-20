# 09. Grafana 대시보드 신/구 스키마 불일치 해소

**날짜**: 2026-08-19
**분석 결과 원문 항목**: 종합 분석 🟠 9번(Grafana 대시보드 스키마 불일치 의심)

## 배경과 재검증 (중요: 이전 보고 내용을 정정함)

원래 분석에서는 `springboot-dashboard.json`이 신형 Grafana v2 스키마(`apiVersion: dashboard.grafana.app/v2`)라
`type: file` 클래식 프로비저너가 못 읽을 수 있고, 패널 datasource uid가 임의값
(`e3e8aca4-...`)으로 하드코딩돼 있어 프로비저닝된 `prometheus` uid와 불일치할 것으로
**추정**했었다.

이번에 실제로 `docker run grafana/grafana:latest`에 이 파일 그대로 마운트해서 검증했다.

- **로그 오류는 실제로 발생함**: `[SHOULD NOT HAPPEN] failed to update managedFields` +
  `.spec.elements.panel-N.kind: field not declared in schema` 수십 줄. v2 스키마를 최신
  Grafana(내부 apiserver/unified storage)가 저장할 때 매니지드필드 추적에 실패하는 게 맞았다.
- **그런데 실제로는 정상 서빙됨**: `GET /api/dashboards/uid/adh6dnj`로 조회해보니 Grafana가
  내부적으로 v2 → 클래식 스키마로 변환해서 패널 36개 전부 정상 반환했다. 즉 로그에 에러가
  쌓이긴 하지만 **대시보드 자체는 깨지지 않고 동작하고 있었다** — "동작 안 할 가능성이
  높다"는 이전 판단은 과장이었다.
- **datasource uid도 실제로는 일치함**: 서빙된 대시보드의 모든 패널이 참조하는 datasource
  uid는 `prometheus` 하나뿐이고, 프로비저닝된 datasource의 uid도 정확히 `prometheus`다.
  파일 안에 있던 `e3e8aca4-...` 문자열은 원본 파일 전체에서 딱 1번 등장하는, 실제 패널이
  참조하지 않는 죽은 메타데이터(구버전 Grafana export가 남긴 흔적)였다 — 이것도 이전
  보고가 부정확했다.

## 조치

로그에 매번 오류가 쌓이는 게 운영상 좋지 않고(장기적으로 신형 스키마 호환이 유지된다는
보장도 없음), 수작업으로 152KB짜리 v2 JSON을 클래식 스키마로 변환하는 건 오류 위험이 커서,
**이미 정상 동작 중인 Grafana가 서빙하는 클래식 스키마 응답을 그대로 받아와 파일을
교체**하는 방법을 썼다.

1. `docker run`으로 이 파일을 그대로 마운트한 Grafana를 띄운다.
2. `GET /api/dashboards/uid/adh6dnj`로 Grafana가 이미 v2→클래식으로 변환해 서빙 중인 JSON을
   그대로 받는다(패널 36개, datasource 참조 전부 검증된 상태).
3. 인스턴스 전용 필드 `id`(데이터베이스 PK, 다른 환경에서는 의미 없음)만 `null`로 지우고
   `monitoring-msa/grafana/provisioning/springboot-dashboard.json`에 덮어쓴다.
4. 새 파일로 다시 Grafana를 띄워 재검증.

## 검증

- 교체 전: 로그에 `[SHOULD NOT HAPPEN]` 오류 다수.
- 교체 후: `docker run`으로 재검증 — `starting to provision dashboards` →
  `finished to provision dashboards`만 나오고 오류 없음. `/api/search`에 두 대시보드
  (`backend-app-logs`, `adh6dnj`) 모두 정상 등록, `adh6dnj`는 패널 36개 그대로 유지.
- `backend-app-logs-dashboard.json`(클래식 스키마)은 원래도 문제없었다 — 손대지 않음.
- 검증에 쓴 임시 컨테이너(`grafana-schema-check`, `grafana-schema-check2`)는 삭제 완료.
