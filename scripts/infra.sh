#!/usr/bin/env bash

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.mysql.yml"
  -f "$ROOT/infra/docker-compose.redis.yml"
  -f "$ROOT/infra/docker-compose.mongo.yml"
  -f "$ROOT/infra/docker-compose.kafka.yml"
  -f "$ROOT/infra/docker-compose.tei.yml"
  -f "$ROOT/infra/docker-compose.qdrant.yml"
  -f "$ROOT/infra/docker-compose.observability.yml")

usage() {
  cat <<'EOF'
Usage: ./scripts/infra.sh <target> <start|stop|restart|status|logs>

Targets:
  mysql, redis, mongo, kafka, tei, qdrant, observability
  user, collector, ai, notification, story, core, all
EOF
}

services() {
  case "$1" in
    mysql|redis|mongo|kafka|qdrant) echo "$1" ;;
    tei) echo "tei-embedding tei-reranker" ;;
    observability) echo "prometheus grafana" ;;
    user) echo "mysql redis" ;;
    collector) echo "mongo" ;;
    ai) echo "mongo kafka" ;;
    notification) echo "mongo redis kafka" ;;
    story) echo "mongo kafka tei-embedding tei-reranker qdrant" ;;
    core) echo "mysql redis mongo kafka" ;;
    all) echo "mysql redis mongo kafka tei-embedding tei-reranker qdrant prometheus grafana" ;;
    *) return 1 ;;
  esac
}

[[ $# -eq 2 ]] || { usage; exit 1; }
TARGET="$1"
ACTION="$2"
SERVICE_NAMES="$(services "$TARGET")" || { usage; exit 1; }
read -r -a SELECTED <<<"$SERVICE_NAMES"

PROFILE="${SPRING_PROFILES_ACTIVE:-local}"

# 셸에 이미 export된 값을 우선하고, 없는 키만 파일에서 채운다.
load_env() {
  local line key
  [[ -f "$1" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]] || continue
    key="${line%%=*}"
    # 값이 빈 줄(KEY=)은 건너뛴다. 빈 문자열을 export하면 Spring이 그것을 값으로 채택해 yaml의 ${KEY:default}가 default 대신 빈 값이 된다.
    [[ -n "${line#*=}" ]] || continue
    [[ -n "${!key+x}" ]] || export "$line"
  done <"$1"
}

load_env "$ROOT/.env.$PROFILE"
load_env "$ROOT/.env"
export SPRING_PROFILES_ACTIVE="$PROFILE"

case "$ACTION" in
  start) "${COMPOSE[@]}" up -d --wait "${SELECTED[@]}" ;;
  stop) "${COMPOSE[@]}" stop "${SELECTED[@]}" ;;
  restart) "${COMPOSE[@]}" restart "${SELECTED[@]}" ;;
  status) "${COMPOSE[@]}" ps "${SELECTED[@]}" ;;
  logs) "${COMPOSE[@]}" logs -f "${SELECTED[@]}" ;;
  *) usage; exit 1 ;;
esac
