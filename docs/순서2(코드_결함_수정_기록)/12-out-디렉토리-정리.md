# 12. out/ 하위 git 추적 대상 정리

**날짜**: 2026-08-19
**분석 결과 원문 항목**: 종합 분석 🟡 (out/production/Groovy 하위 9개 파일이 .gitignore 등록 후에도 계속 추적됨)

## 배경

`.gitignore`에 `out/`이 등록되어 있음에도, `out/production/Groovy/` 하위 9개 파일
(`gradlew`, `build.gradle`, `settings.gradle`, `gradle-wrapper.jar` 등 — 레거시 모놀리스
`Groovy` 모듈의 IDE 빌드 산출물로 보임)이 계속 git에 추적되고 있었다. `.gitignore` 규칙은
소급 적용되지 않으므로(`out/`이 gitignore에 등록되기 전에 이미 커밋된 파일이라 계속 추적됨)
발생한 문제 — 최초 커밋은 2026-07-13(`chore(#2): 백엔드 프로젝트 초기 설정`)이었다.

## 확인한 것

- 내용 자체엔 시크릿/자격증명 없음(단순 gradle wrapper + 빈 `application.yml`).
- 파일 전부가 gitignore 등록 시점 이전부터 손대지 않은 스냅샷이라, 실수로 커밋된 빌드
  산출물이 맞고 별도로 보존할 가치가 없다고 판단.

## 조치

`git rm -r --cached out/`로 9개 파일을 추적에서 제거하고, `rm -rf out/`로 로컬 디스크에서도
삭제(gitignore된 `.gradle/` 캐시 등 추가 생성물 포함 전부 제거).

## 검증

`git status --short`로 9개 파일 전부 `D`(staged deletion)로 표시됨을 확인, `out/` 디렉토리
자체가 더 이상 존재하지 않음을 확인.
