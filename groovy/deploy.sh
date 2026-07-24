#!/usr/bin/env bash
# Groovy 백엔드 전체 스택(MySQL + Backend) 빌드/배포 스크립트
#
# 사용법:
#   ./deploy.sh          최신 소스로 이미지를 빌드하고 전체 스택을 기동/재배포한다.
#   ./deploy.sh down     전체 스택을 정지한다(데이터 볼륨은 보존).
#   ./deploy.sh logs     백엔드 컨테이너 로그를 스트리밍한다.
#
# 주의: 단일 백엔드 컨테이너 구성이므로 배포 시 컨테이너 교체(수 초) 동안 짧은 다운타임이 발생한다.
#       완전한 무중단 배포를 위해서는 로드밸런서 뒤에 최소 2개 이상의 백엔드 인스턴스를 두는
#       구성이 필요하며, 이는 현재 스코프를 벗어난다.
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE="docker compose -f docker-compose.yml"

if [ ! -f .env ]; then
	echo "[deploy] .env 파일이 없습니다. 아래 명령으로 먼저 생성/설정하세요:" >&2
	echo "         cp .env.example .env" >&2
	exit 1
fi

case "${1:-up}" in
	down)
		echo "[deploy] 전체 스택 정지"
		$COMPOSE down
		;;
	logs)
		$COMPOSE logs -f backend
		;;
	up)
		echo "[deploy] 1/2 최신 소스로 이미지 빌드"
		$COMPOSE build backend

		echo "[deploy] 2/2 MySQL(healthcheck 통과) -> Backend 순서로 기동/재배포"
		# depends_on(condition: service_healthy) + HEALTHCHECK 덕분에
		# --wait 한 번으로 "DB 정상 확인 후 백엔드 기동" 순서가 보장된다.
		$COMPOSE up -d --wait

		echo "[deploy] 완료. 컨테이너 상태:"
		$COMPOSE ps
		;;
	*)
		echo "사용법: $0 [up|down|logs]" >&2
		exit 1
		;;
esac
