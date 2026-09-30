#!/usr/bin/env bash

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
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

SERVICES=(user-service collector-service story-service ai-service notification-service notification-worker notification-routing)

usage() {
  echo "Usage: [SPRING_PROFILES_ACTIVE=<profile>] ./scripts/app.sh <service|all> <start|stop|restart|status|logs>"
}

is_service() {
  local service
  for service in "${SERVICES[@]}"; do
    [[ "$service" == "$1" ]] && return 0
  done
  return 1
}

pid_file() { echo "$ROOT/.run/$1.pid"; }
log_file() { echo "$ROOT/logs/$1.log"; }

running_pid() {
  local file
  local pid
  file="$(pid_file "$1")"
  [[ -f "$file" ]] || return 1
  pid="$(<"$file")"
  kill -0 "$pid" 2>/dev/null || return 1
  echo "$pid"
}

start() {
  local service="$1"
  local pid
  if pid="$(running_pid "$service")"; then
    echo "$service is already running (PID $pid)."
    return
  fi

  mkdir -p "$ROOT/.run" "$ROOT/logs"
  cd "$ROOT"
  nohup ./gradlew --no-daemon ":$service:bootRun" >"$(log_file "$service")" 2>&1 &
  echo $! >"$(pid_file "$service")"
  echo "$service started (PID $!, profile $PROFILE). Log: $(log_file "$service")"
}

stop() {
  local service="$1"
  local pid
  if ! pid="$(running_pid "$service")"; then
    echo "$service is not running."
    return
  fi
  kill "$pid"
  for _ in $(seq 1 30); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
  rm -f "$(pid_file "$service")"
  echo "$service stopped."
}

status() {
  local pid
  if pid="$(running_pid "$1")"; then
    echo "$1: running (PID $pid)"
  else
    echo "$1: stopped"
  fi
}

logs() {
  local file
  file="$(log_file "$1")"
  [[ -f "$file" ]] || { echo "$1 has no log yet." >&2; return 1; }
  tail -n 100 -f "$file"
}

execute() {
  case "$2" in
    start) start "$1" ;;
    stop) stop "$1" ;;
    restart) stop "$1"; start "$1" ;;
    status) status "$1" ;;
    logs) logs "$1" ;;
    *) usage; return 1 ;;
  esac
}

[[ $# -eq 2 ]] || { usage; exit 1; }
TARGET="$1"
ACTION="$2"

if [[ "$TARGET" == "all" ]]; then
  [[ "$ACTION" != "logs" ]] || { echo "Select one service for logs." >&2; exit 1; }
  for service in "${SERVICES[@]}"; do execute "$service" "$ACTION"; done
elif is_service "$TARGET"; then
  execute "$TARGET" "$ACTION"
else
  usage
  exit 1
fi
