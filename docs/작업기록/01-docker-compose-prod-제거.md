# 01. docker-compose.prod.yml 제거

**날짜**: 2026-08-19
**분석 결과 원문 항목**: 종합 분석 🔴 1번(운영 환경에 메트릭·알림 파이프라인 전체가 없음)

## 배경

`docker-compose.prod.yml`은 이 저장소에 있었지만 실제 운영 배포용 compose/env 파일은 운영
서버에서 직접 작성·관리한다(사용자 확인). CI 배포 잡(`.github/workflows/docker-build-push-frontend.yml`)도
`runs-on: self-hosted` 러너의 `/home/shared` 아래 파일을 쓰지, 이 저장소를 체크아웃해서 쓰지
않는다 — 즉 저장소의 `docker-compose.prod.yml`은 실제로 아무 것도 배포하지 않는 죽은 사본이었고,
심지어 `prometheus`/`alertmanager`/`cadvisor`/`node-exporter`/`mysqld-exporter`가 통째로
빠져 있어 로컬 파일 기준으로 실수로 그대로 배포하면 관측성 파이프라인이 죽은 채로 뜨는 상태였다.

## 조치

- `docker-compose.prod.yml` 삭제.
- `docker-compose.local.yml`, `docker-compose.example.yml`에 남아있던 "운영 배포는
  docker-compose.prod.yml 참고" 식의 직접 참조 주석 정리(삭제된 파일을 다시 가리키는 새 고아
  참조가 생기지 않도록).
- `docs/Groovy_MSA_구조와실행.md`("현재 상태"를 설명하는 최신 구조 문서)의 "세 가지 Compose
  파일" 표/실행 명령에서 prod 항목 제거, 운영 compose는 서버에서 별도 관리한다는 안내와
  [02](./02-원본-레포-통합-주의점.md) 링크 추가.
- `docs/Groovy_MSA_저장소_구조.md`의 최상위 파일 목록도 동일하게 정리.

## 의도적으로 손대지 않은 것

- `README.md`: MSA 전환 이전(레거시 `groovy/` 디렉토리 기준) 내용이 이미 전면적으로 낡아 있어,
  이번 항목 범위를 넘는 별도의 큰 작업(전체 재작성)이 필요함. 후속 작업으로 남겨둠.
- `docs/Groovy_MSA_Phase4/5/6_*.md`: 과거 작업 기록(히스토리)이라 원문 그대로 보존.
- `.github/workflows/docker-build-push-frontend.yml`: 운영 서버 내부 파일을 가리키는 참조라 그대로 둠.

## 검증

`grep -rln "docker-compose.prod"`로 남은 참조를 확인 — README.md(별도 작업 대상)와 과거
Phase 문서, CI 워크플로우(의도적으로 유지)만 남았음을 확인.
