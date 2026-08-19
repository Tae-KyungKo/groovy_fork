# 05. alert.rules.yml 스텁 규칙 실제 지표로 교체

**날짜**: 2026-08-19
**분석 결과 원문 항목**: 종합 분석 🔴 5번(알림 규칙 2개가 사실상 스텁)

## 배경

`monitoring-msa/prometheus/alert.rules.yml`의 세 규칙에 문제가 있었다.

- `HikariCpuPoolPendingDetected`, `BackendCpuSpikeDetected`: `expr: 1`로 상시 참(실제 지표를
  전혀 참조하지 않는 플레이스홀더).
- `BackendMemoryUsageTooHigh`: 실제 지표(`jvm_memory_used_bytes`/`jvm_memory_max_bytes`)를
  쓰지만 `sum(...)`으로 6개 서비스를 합산해버려서, 서비스 하나가 한계치에 도달해도 나머지
  서비스에 의해 희석돼 알림이 안 뜰 수 있었다.

## 조치

- `HikariCpuPoolPendingDetected`: `sum by (job) (hikaricp_connections_pending) > 0`로 교체
  — HikariCP가 실제로 노출하는 pending 커넥션 지표를 서비스별로(`by (job)`) 본다.
- `BackendMemoryUsageTooHigh`: `sum by (job) (...) / sum by (job) (...)`로 변경 — 서비스별로
  따로 계산해서 한 서비스만 위험해도 그 서비스의 `job` 라벨로 알림이 뜨게 함.
- `BackendCpuSpikeDetected`: `process_cpu_usage > 0.95`로 교체 — Micrometer가 노출하는 실제
  프로세스 CPU 사용률 지표.
- 세 규칙 모두 `for: 10s` → `for: 1m`로 늘림 — 10초는 순간 스파이크에도 알림이 튀는
  flapping이 생기기 쉬움.
- annotations의 `description`에 `{{ $labels.job }}`/`{{ $value }}`(또는
  `humanizePercentage`) 템플릿을 넣어 Slack 알림에 어느 서비스에서 어떤 값으로 발동했는지
  바로 보이게 함(기존엔 서비스명 없이 고정 문구뿐이었음).

## 검증

- `python3 -c "import yaml; yaml.safe_load(...)"`로 YAML 문법 확인.
- `docker run --rm --entrypoint promtool prom/prometheus:latest check rules
  /rules/alert.rules.yml` → `SUCCESS: 3 rules found`로 PromQL 표현식까지 실제 검증.
- 지표 이름(`hikaricp_connections_pending`, `process_cpu_usage`, `jvm_memory_used_bytes`,
  `jvm_memory_max_bytes`)은 모두 표준 Micrometer 바인더가 노출하는 이름이며, 6개 서비스
  전부 `micrometer-registry-prometheus` 의존성과 `management.endpoints.web.exposure.include:
  health,prometheus`가 이미 설정되어 있음(이전 모니터링 분석에서 확인됨)을 근거로 함.
- Kafka/Prometheus가 실제로 뜬 상태에서 임계치를 넘겨 알림이 실제로 발동하는지까지는 이
  환경에서 end-to-end로 검증하지 못함 — `docker-compose.local.yml` 스택을 띄운 뒤 수동
  검증을 권장.
