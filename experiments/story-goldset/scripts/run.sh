#!/usr/bin/env bash
# 골드셋 실험을 단계별로 돌린다.
#   extract   로컬 Mongo에서 요약·기사를 뽑아 쌍 250개를 만든다
#   draft     Ollama(qwen3.8:27b)로 초안을 붙이고 검토표를 만든다
#   review    검토표를 다시 만든다.  apply  검토표의 확정 칸을 pairs.jsonl로 옮긴다
#   score     DJL bge-m3·bge-reranker-v2-m3로 점수를 낸다 (첫 실행에 모델 3.6GB를 받는다)
#   evaluate  θ 조합을 평가해 results/thresholds.md를 만든다
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT="$(cd "$EXP_DIR/../.." && pwd)"
MONGO_CONTAINER="${MONGO_CONTAINER:-kachi-mongo}"

usage() {
  cat <<'USAGE'
Usage: ./scripts/run.sh <extract|draft|review|apply|score|evaluate> [options]
  extract  [--force]                       기존 goldset/pairs.jsonl이 있으면 --force 없이는 덮어쓰지 않는다
  draft                                    OLLAMA_BASE_URL(기본 http://localhost:11434), OLLAMA_MODEL(기본 qwen3.8:27b)
  review                                   results/review.md를 다시 만든다 (점수가 있으면 코사인 순)
  apply                                    review.md의 확정 칸을 goldset/pairs.jsonl의 label로 옮긴다
  score    [--embedding bge|e5]            기본 bge
  evaluate [--use-draft] [--embedding bge|e5]
USAGE
}

[[ $# -ge 1 ]] || { usage; exit 1; }
COMMAND="$1"
shift

export_mongo() {
  mkdir -p "$EXP_DIR/generated"
  docker exec "$MONGO_CONTAINER" mongosh --quiet kachi_ai --eval '
    db.news_summaries.find({}, {keyword: 1, sourceNewsIds: 1, title: 1, createdAt: 1}).forEach(d => print(JSON.stringify({
      id: String(d._id), keyword: d.keyword, sourceNewsIds: d.sourceNewsIds, title: d.title, createdAt: d.createdAt.toISOString()
    })))' > "$EXP_DIR/generated/summaries.jsonl"
  docker exec "$MONGO_CONTAINER" mongosh --quiet kachi_collector --eval '
    db.news.find({}, {title: 1, excerpt: 1, source: 1, language: 1, collectedAt: 1, matchedKeywords: 1}).forEach(d => print(JSON.stringify({
      newsId: String(d._id), title: d.title, excerpt: d.excerpt, source: d.source, language: d.language,
      collectedAt: d.collectedAt.toISOString(), matchedKeywords: d.matchedKeywords
    })))' > "$EXP_DIR/generated/news.jsonl"
  echo "exported summaries=$(wc -l < "$EXP_DIR/generated/summaries.jsonl") news=$(wc -l < "$EXP_DIR/generated/news.jsonl")"
}

case "$COMMAND" in
  extract) export_mongo ;;
  draft|review|apply|score|evaluate) ;;
  *) usage; exit 1 ;;
esac

"$ROOT/gradlew" -p "$EXP_DIR" -q run --args="$COMMAND $*"
