#!/usr/bin/env bash
# REST와 gRPC 전송 방식을 같은 부하로 비교한다.
#   up      실험용 Qdrant 컨테이너를 띄운다
#   bench   두 전송 방식으로 upsert·search를 재고 results/transport.jsonl에 남긴다
#   report  측정값을 results/transport.md 표로 만든다
#   down    컨테이너와 볼륨을 지운다
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT="$(cd "$EXP_DIR/../.." && pwd)"

usage() {
  cat <<'USAGE'
Usage: ./scripts/run.sh <up|bench|report|down>
  up                                             QDRANT_TRANSPORT_EXPERIMENT_PORT(기본 6343), QDRANT_TRANSPORT_EXPERIMENT_GRPC_PORT(기본 6344)
  bench [--points N] [--queries N] [--repeats N]  컨테이너가 떠 있어야 한다. 확정 스펙 부하는 약 6분 걸린다
  report                                         bench 없이 기존 results/transport.jsonl만 다시 표로 만든다
  down                                           볼륨까지 지운다
USAGE
}

[[ $# -ge 1 ]] || { usage; exit 1; }
COMMAND="$1"
shift

case "$COMMAND" in
  up)
    docker compose -f "$EXP_DIR/docker-compose.yml" up -d
    until curl -sf "http://localhost:${QDRANT_TRANSPORT_EXPERIMENT_PORT:-6343}/readyz" >/dev/null; do sleep 1; done
    echo "qdrant ready on ${QDRANT_TRANSPORT_EXPERIMENT_PORT:-6343}(rest) ${QDRANT_TRANSPORT_EXPERIMENT_GRPC_PORT:-6344}(grpc)"
    ;;
  down)
    docker compose -f "$EXP_DIR/docker-compose.yml" down -v
    ;;
  bench|report)
    "$ROOT/gradlew" -p "$EXP_DIR" -q run --args="$COMMAND $*"
    ;;
  *) usage; exit 1 ;;
esac
