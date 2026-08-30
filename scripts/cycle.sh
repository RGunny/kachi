#!/usr/bin/env bash
#
# 전체 사이클 스모크용 기동 스크립트.
# 인프라(core) → Mongo index → user-service → 나머지 서비스 순으로 띄운다.
# ai-service는 local 프로파일이 꺼 둔 events·relay를 켜고 LLM provider를 groq 하나로 좁힌다.
# 절차와 확인 항목은 docs/전체-사이클-스모크.md에 있다.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA="$ROOT/scripts/infra.sh"
APP="$ROOT/scripts/app.sh"
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

MONGO_CONTAINER="${MONGO_CONTAINER:-kachi-mongo}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kachi-kafka}"
USER_SERVICE_URL="${KACHI_USER_SERVICE_BASE_URL:-http://localhost:8080}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

# user-service 다음에 띄우는 서비스. 넷은 기동 시 user-service 주소만 필요하고 실제 호출은 요청이 있을 때 한다.
FOLLOWERS=(collector-service ai-service notification-routing notification-service notification-worker)

usage() {
  cat <<'USAGE'
Usage: ./scripts/cycle.sh <start|stop|status>

  start   인프라(mysql redis mongo kafka) → Mongo index → user-service → 나머지 5개 순으로 띄운다.
  stop    서비스 6개를 내리고 인프라를 내린다.
  status  서비스와 인프라 상태를 본다.

  ai-service에는 KACHI_AI_EVENTS_ENABLED=true, KACHI_AI_OUTBOX_RELAY_ENABLED=true,
  groq 외 provider enabled=false 를 env로 덮어 넣는다. scheduler는 켜지 않는다.
USAGE
}

wait_for_health() {
  local url="$1/actuator/health"
  local name="$2"
  local i
  for ((i = 0; i < HEALTH_TIMEOUT; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$name is healthy."
      return 0
    fi
    sleep 1
  done
  echo "$name did not become healthy in ${HEALTH_TIMEOUT}s: $url" >&2
  return 1
}

# compose의 kafka에는 healthcheck가 없어 --wait가 broker를 기다리지 않는다. 토픽 목록이 나올 때까지 기다린다.
wait_for_kafka() {
  local i
  for ((i = 0; i < 60; i++)); do
    if docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
      echo "kafka is ready."
      return 0
    fi
    sleep 1
  done
  echo "kafka did not become ready in 60s" >&2
  return 1
}

# 어댑터의 auto-index-creation이 만들지 않는 index는 스크립트로 만든다. 여러 번 실행해도 같은 결과다.
create_mongo_indexes() {
  local script
  for script in notification-indexes.js notification-routing-indexes.js; do
    docker exec -i "$MONGO_CONTAINER" mongosh --quiet <"$ROOT/infra/mongo/$script" >/dev/null \
      && echo "mongo index applied: $script" \
      || { echo "mongo index failed: $script" >&2; return 1; }
  done
}

start() {
  "$INFRA" core start || return 1
  wait_for_kafka || return 1
  create_mongo_indexes || return 1

  "$APP" user-service start
  wait_for_health "$USER_SERVICE_URL" user-service || return 1

  # ai-service: 요약 이벤트 발행과 relay를 켜고 실제 호출 provider를 groq 하나로 좁힌다.
  export KACHI_AI_EVENTS_ENABLED="${KACHI_AI_EVENTS_ENABLED:-true}"
  export KACHI_AI_OUTBOX_RELAY_ENABLED="${KACHI_AI_OUTBOX_RELAY_ENABLED:-true}"
  export KACHI_AI_PROVIDERS_OPENROUTER_ENABLED="${KACHI_AI_PROVIDERS_OPENROUTER_ENABLED:-false}"
  export KACHI_AI_PROVIDERS_TOGETHER_ENABLED="${KACHI_AI_PROVIDERS_TOGETHER_ENABLED:-false}"
  export KACHI_AI_PROVIDERS_CEREBRAS_ENABLED="${KACHI_AI_PROVIDERS_CEREBRAS_ENABLED:-false}"
  export KACHI_AI_PROVIDERS_MISTRAL_ENABLED="${KACHI_AI_PROVIDERS_MISTRAL_ENABLED:-false}"

  local service
  for service in "${FOLLOWERS[@]}"; do
    "$APP" "$service" start
  done
  wait_for_health "http://localhost:8082" collector-service
  wait_for_health "http://localhost:8083" ai-service
  wait_for_health "http://localhost:8086" notification-routing
  wait_for_health "http://localhost:8084" notification-service
  wait_for_health "http://localhost:8085" notification-worker
}

stop() {
  "$APP" all stop
  "$INFRA" core stop
}

status() {
  "$APP" all status
  "$INFRA" core status
}

[[ $# -eq 1 ]] || { usage; exit 1; }
case "$1" in
  start) start ;;
  stop) stop ;;
  status) status ;;
  *) usage; exit 1 ;;
esac
