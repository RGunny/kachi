#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROWS="${1:-100000}"
OUT_DIR="$EXP_DIR/generated/$ROWS"
RESULT_JSON="$EXP_DIR/results/${ROWS}-summary.json"
RESULT_MD="$EXP_DIR/results/${ROWS}-summary.md"
CASE_ROWS="$EXP_DIR/results/${ROWS}-case-rows.tmp"
CASE_JSONS="$EXP_DIR/results/${ROWS}-case-jsons.tmp"
DB="news_hash_experiment"

COMPOSE=(docker compose -f "$EXP_DIR/docker-compose.yml")
MONGOSH=("${COMPOSE[@]}" exec -T mongo mongosh "mongodb://localhost:27017/$DB" --quiet)
MONGOIMPORT=("${COMPOSE[@]}" exec -T mongo mongoimport --uri "mongodb://localhost:27017/$DB")

wait_mongo() {
  local retries=60
  while [ "$retries" -gt 0 ]; do
    if "${COMPOSE[@]}" exec -T mongo mongosh --quiet --eval "db.runCommand({ ping: 1 }).ok" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    retries=$((retries - 1))
  done
  echo "MongoDB not ready" >&2
  return 1
}

run_case() {
  local case_name="$1"
  local collection="$2"
  local unique_index_sql="$3"
  local case_result="$EXP_DIR/results/${ROWS}-${case_name}.json"

  "${MONGOSH[@]}" --eval "db.$collection.drop(); db.$collection.createIndex({createdAt:1}); $unique_index_sql" >/dev/null

  local first
  local second
  local added
  first="$(import_file "$collection" "$OUT_DIR/first-run.jsonl")"
  second="$(import_file "$collection" "$OUT_DIR/second-run.jsonl")"
  added="$(import_file "$collection" "$OUT_DIR/added-news-run.jsonl")"

  local stats
  stats="$("${MONGOSH[@]}" --eval "const s=db.$collection.stats(); JSON.stringify({count:db.$collection.countDocuments(),storageSize:s.storageSize,totalIndexSize:s.totalIndexSize})")"
  local final_count
  local index_size
  local storage_size
  final_count="$(echo "$stats" | sed -n 's/.*"count":\([0-9][0-9]*\).*/\1/p')"
  index_size="$(echo "$stats" | sed -n 's/.*"totalIndexSize":\([0-9][0-9]*\).*/\1/p')"
  storage_size="$(echo "$stats" | sed -n 's/.*"storageSize":\([0-9][0-9]*\).*/\1/p')"

  cat > "$case_result" <<JSON
{
  "case": "$case_name",
  "collection": "$collection",
  "rows": $ROWS,
  "firstRun": $first,
  "secondRun": $second,
  "addedNewsRun": $added,
  "stats": $stats
}
JSON

  echo "| $case_name | $(json_value "$first" inserted) | $(json_value "$first" elapsedMillis) | $(json_value "$second" inserted) | $(json_value "$second" failed) | $(json_value "$second" elapsedMillis) | $(json_value "$added" inserted) | $(json_value "$added" failed) | $(json_value "$added" elapsedMillis) | $final_count | $storage_size | $index_size |" >> "$CASE_ROWS"
  cat "$case_result" >> "$CASE_JSONS"
  echo "," >> "$CASE_JSONS"
}

import_file() {
  local collection="$1"
  local file="$2"
  local started
  local elapsed
  local before_count
  local after_count
  local attempted
  local log_file

  attempted="$ROWS"
  before_count="$("${MONGOSH[@]}" --eval "db.$collection.countDocuments()")"
  log_file="$EXP_DIR/results/${ROWS}-${collection}-$(basename "$file").log"
  started="$(date +%s)"
  set +e
  "${MONGOIMPORT[@]}" \
    --collection "$collection" \
    --file "/experiment/generated/$ROWS/$(basename "$file")" \
    --type json \
    --mode insert \
    --quiet > "$log_file" 2>&1
  local status=$?
  set -e
  elapsed=$(( ($(date +%s) - started) * 1000 ))
  after_count="$("${MONGOSH[@]}" --eval "db.$collection.countDocuments()")"

  local inserted=$((after_count - before_count))
  local failed=$((attempted - inserted))

  cat <<JSON
{"status":$status,"inserted":$inserted,"failed":$failed,"elapsedMillis":$elapsed}
JSON
}

json_value() {
  local json="$1"
  local key="$2"
  echo "$json" | sed -n "s/.*\"$key\":\\([0-9][0-9]*\\).*/\\1/p"
}

write_summary() {
  {
    echo "{"
    echo "  \"rows\": $ROWS,"
    echo "  \"cases\": ["
    sed '$ s/,$//' "$CASE_JSONS"
    echo "  ]"
    echo "}"
  } > "$RESULT_JSON"

  {
    echo "# newsHash deduplication result ($ROWS)"
    echo
    echo "| 조건 | 1회차 저장 | 1회차 ms | 2회차 저장 | 2회차 실패 | 2회차 ms | 뉴스 추가 저장 | 뉴스 추가 실패 | 뉴스 추가 ms | 최종 수 | storage size | index size |"
    echo "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
    cat "$CASE_ROWS"
  } > "$RESULT_MD"
}

java --source 21 "$SCRIPT_DIR/GenerateNewsHashDataset.java" \
  --rows "$ROWS" \
  --raw-dir "$EXP_DIR/raw" \
  --out "$OUT_DIR"

"${COMPOSE[@]}" up -d

wait_mongo

: > "$CASE_ROWS"
: > "$CASE_JSONS"

run_case "no_unique" "news_summaries_no_unique" ""
run_case "sourceNewsIds_array_unique" "news_summaries_source_news_ids_array_unique" "db.news_summaries_source_news_ids_array_unique.createIndex({keyword:1,summaryFrom:1,summaryTo:1,sourceNewsIds:1,promptVersion:1,model:1},{unique:true})"
run_case "sourceNewsIds_canonical_unique" "news_summaries_source_news_ids_canonical_unique" "db.news_summaries_source_news_ids_canonical_unique.createIndex({keyword:1,summaryFrom:1,summaryTo:1,sourceNewsIdsCanonical:1,promptVersion:1,model:1},{unique:true})"
run_case "keyword_newsHash_prompt_model_unique" "news_summaries_news_hash_unique" "db.news_summaries_news_hash_unique.createIndex({keyword:1,newsHash:1,promptVersion:1,model:1},{unique:true})"

write_summary
