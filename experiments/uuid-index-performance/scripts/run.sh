#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE=(docker compose -f "$EXP_DIR/docker-compose.yml")
DB=uuid_perf
MYSQL=(mysql --protocol=tcp -h 127.0.0.1 --local-infile=1 -uroot -proot -N -B "$DB")

ROWS_LIST=(${ROWS_LIST:-100000 1000000})
QUERY_REPEAT="${QUERY_REPEAT:-5}"
BATCH_SIZE="${BATCH_SIZE:-1000}"
CASES=(bigint bin16_uuid_v7 char36_uuid_v7 prefix_id)

service_of() {
    case "$1" in
        bigint) echo "mysql_bigint" ;;
        bin16_uuid_v7) echo "mysql_bin16" ;;
        char36_uuid_v7) echo "mysql_char36" ;;
        prefix_id) echo "mysql_prefix" ;;
    esac
}

csv_of() {
    case "$1" in
        bigint) echo "bigint.csv" ;;
        bin16_uuid_v7) echo "bin16_uuid_v7.csv" ;;
        char36_uuid_v7) echo "char36_uuid_v7.csv" ;;
        prefix_id) echo "prefix_id.csv" ;;
    esac
}

mysql_exec() {
    local service="$1"
    shift
    "${COMPOSE[@]}" exec -T "$service" "${MYSQL[@]}" "$@" 2>/dev/null
}

wait_mysql() {
    local service="$1"
    local retries=60
    while [ "$retries" -gt 0 ]; do
        if "${COMPOSE[@]}" exec -T "$service" mysqladmin ping -h 127.0.0.1 -uroot -proot >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
        retries=$((retries - 1))
    done
    echo "MySQL not ready: $service" >&2
    return 1
}

schema_sql() {
    local id_type
    case "$1" in
        bigint) id_type="bigint not null auto_increment" ;;
        bin16_uuid_v7) id_type="binary(16) not null" ;;
        char36_uuid_v7) id_type="char(36) not null" ;;
        prefix_id) id_type="varchar(64) not null" ;;
    esac

    cat <<SQL
drop table if exists records;
create table records (
    id $id_type,
    service_type varchar(20) not null,
    created_at datetime(6) not null,
    payload varchar(100) not null,
    primary key (id),
    key idx_records_created_at (created_at desc),
    key idx_records_service_id (service_type, id desc)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
SQL
}

load_data_sql() {
    local case_name="$1"
    local csv
    csv="$(csv_of "$case_name")"

    case "$case_name" in
        bigint)
            cat <<SQL
set @t0 := now(6);
load data local infile '/experiment/generated/$csv'
into table records
fields terminated by ','
lines terminated by '\n'
(service_type, created_at, payload);
select timestampdiff(microsecond, @t0, now(6));
SQL
            ;;
        bin16_uuid_v7)
            cat <<SQL
set @t0 := now(6);
load data local infile '/experiment/generated/$csv'
into table records
fields terminated by ','
lines terminated by '\n'
(@id, service_type, created_at, payload)
set id = unhex(@id);
select timestampdiff(microsecond, @t0, now(6));
SQL
            ;;
        *)
            cat <<SQL
set @t0 := now(6);
load data local infile '/experiment/generated/$csv'
into table records
fields terminated by ','
lines terminated by '\n'
(id, service_type, created_at, payload);
select timestampdiff(microsecond, @t0, now(6));
SQL
            ;;
    esac
}

build_batch_sql() {
    local case_name="$1"
    local csv="$EXP_DIR/generated/$(csv_of "$case_name")"
    local out="$EXP_DIR/generated/batch_${case_name}.sql"

    case "$case_name" in
        bigint)
            awk -v batch="$BATCH_SIZE" -F',' '
                BEGIN { n=0 }
                n % batch == 0 { if (n > 0) print ";"; printf "insert into records(service_type, created_at, payload) values " }
                n % batch != 0 { printf "," }
                { printf "(\047%s\047,\047%s\047,\047%s\047)", $1, $2, $3; n++ }
                END { print ";" }
            ' "$csv" > "$out"
            ;;
        bin16_uuid_v7)
            awk -v batch="$BATCH_SIZE" -F',' '
                BEGIN { n=0 }
                n % batch == 0 { if (n > 0) print ";"; printf "insert into records(id, service_type, created_at, payload) values " }
                n % batch != 0 { printf "," }
                { printf "(unhex(\047%s\047),\047%s\047,\047%s\047,\047%s\047)", $1, $2, $3, $4; n++ }
                END { print ";" }
            ' "$csv" > "$out"
            ;;
        *)
            awk -v batch="$BATCH_SIZE" -F',' '
                BEGIN { n=0 }
                n % batch == 0 { if (n > 0) print ";"; printf "insert into records(id, service_type, created_at, payload) values " }
                n % batch != 0 { printf "," }
                { printf "(\047%s\047,\047%s\047,\047%s\047,\047%s\047)", $1, $2, $3, $4; n++ }
                END { print ";" }
            ' "$csv" > "$out"
            ;;
    esac

    echo "$out"
}

median_from_file() {
    sort -n "$1" | awk '{ a[++n]=$1 } END { if (n%2) print a[(n+1)/2]; else printf "%.0f\n", (a[n/2]+a[n/2+1])/2 }'
}

measure_query() {
    local service="$1"
    local case_name="$2"
    local query_name="$3"
    local id_expr="$4"
    local query tmp

    case "$query_name" in
        pk_lookup) query="select sum(length(payload) + length(service_type)) from records where id = $id_expr" ;;
        order_by_id) query="select sum(length(payload) + length(service_type)) from (select payload, service_type from records order by id desc limit 100) q" ;;
        order_by_created_at) query="select sum(length(payload) + length(service_type)) from (select payload, service_type from records order by created_at desc limit 100) q" ;;
    esac

    tmp="$EXP_DIR/generated/query_${case_name}_${query_name}.values"
    : > "$tmp"
    for _ in $(seq 1 "$QUERY_REPEAT"); do
        mysql_exec "$service" <<SQL | awk -F'\t' '$1 == "elapsed_us" { print $2 }' >> "$tmp"
set @t0 := now(6);
$query;
select 'elapsed_us', timestampdiff(microsecond, @t0, now(6));
SQL
    done
    median_from_file "$tmp"
}

explain_sql_for() {
    case "$1" in
        pk_lookup) echo "explain analyze select * from records where id = $2;" ;;
        order_by_id) echo "explain analyze select * from records order by id desc limit 100;" ;;
        order_by_created_at) echo "explain analyze select * from records order by created_at desc limit 100;" ;;
    esac
}

format_s() {
    awk -v us="$1" 'BEGIN { printf "%.3f", us / 1000000 }'
}

format_mb() {
    awk -v bytes="$1" 'BEGIN { printf "%.2f", bytes / 1024 / 1024 }'
}

id_expr_for() {
    local service="$1"
    local case_name="$2"
    case "$case_name" in
        bigint) mysql_exec "$service" -e "select id from records order by id desc limit 1;" ;;
        bin16_uuid_v7) echo "unhex('$(mysql_exec "$service" -e "select hex(id) from records order by id desc limit 1;")')" ;;
        *) echo "'$(mysql_exec "$service" -e "select id from records order by id desc limit 1;")'" ;;
    esac
}

write_plan_summary() {
    cat > "$EXP_DIR/generated/query-plans.raw.md" <<'MD'
# Query Plans

`EXPLAIN ANALYZE` raw output.

## 요약

| 쿼리 | 확인할 것 | 결과 |
|---|---|---|
| PK lookup | `PRIMARY`로 단건 조회되는가 | 모든 실험군 `Rows fetched before execution` |
| `ORDER BY id DESC LIMIT 100` | PK reverse scan이 되는가 | 모든 실험군 `PRIMARY (reverse)` index scan |
| `ORDER BY created_at DESC LIMIT 100` | `created_at` 보조 인덱스를 타는가 | 모든 실험군 `idx_records_created_at` index scan |

MD
}

append_plan_raw() {
    local rows="$1"
    local case_name="$2"
    local service="$3"
    local id_expr="$4"

    {
        echo "## rows=$rows / $case_name"
        echo
        for query_name in pk_lookup order_by_id order_by_created_at; do
            echo "### $query_name"
            echo
            explain_sql_for "$query_name" "$id_expr" | mysql_exec "$service"
            echo
        done
    } >> "$EXP_DIR/generated/query-plans.raw.md"
}

mkdir -p "$EXP_DIR/generated"
echo -e "rows\tcase\tdata_mb\tindex_mb\ttotal_mb\tload_s\tbatch_s\tpk_lookup_ms\torder_by_id_ms\torder_by_created_at_ms" > "$EXP_DIR/generated/summary.tsv"
write_plan_summary

echo "[run] clean start"
"${COMPOSE[@]}" down -v >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d >/dev/null
for case_name in "${CASES[@]}"; do
    wait_mysql "$(service_of "$case_name")"
done

for rows in "${ROWS_LIST[@]}"; do
    echo "[run] generate rows=$rows"
    (cd "$EXP_DIR" && java --source 21 scripts/GenerateDataset.java --rows "$rows" --out generated)

    for case_name in "${CASES[@]}"; do
        service="$(service_of "$case_name")"
        echo "[run] rows=$rows case=$case_name"

        schema_sql "$case_name" | mysql_exec "$service"
        load_us="$(load_data_sql "$case_name" | mysql_exec "$service" | awk 'NF == 1 && $1 ~ /^[0-9]+$/ { print $1 }' | tail -1)"
        mysql_exec "$service" -e "analyze table records;" >/dev/null

        read -r data_bytes index_bytes total_bytes < <(mysql_exec "$service" <<'SQL'
select data_length, index_length, data_length + index_length
from information_schema.tables
where table_schema = database()
  and table_name = 'records';
SQL
)

        id_expr="$(id_expr_for "$service" "$case_name")"
        pk_us="$(measure_query "$service" "$case_name" pk_lookup "$id_expr")"
        order_id_us="$(measure_query "$service" "$case_name" order_by_id "$id_expr")"
        order_created_us="$(measure_query "$service" "$case_name" order_by_created_at "$id_expr")"
        append_plan_raw "$rows" "$case_name" "$service" "$id_expr"

        schema_sql "$case_name" | mysql_exec "$service"
        batch_sql="$(build_batch_sql "$case_name")"
        start_ns="$(date +%s%N)"
        mysql_exec "$service" < "$batch_sql"
        end_ns="$(date +%s%N)"
        batch_us="$(awk -v start="$start_ns" -v end="$end_ns" 'BEGIN { printf "%.0f", (end - start) / 1000 }')"

        echo -e "$rows\t$case_name\t$(format_mb "$data_bytes")\t$(format_mb "$index_bytes")\t$(format_mb "$total_bytes")\t$(format_s "$load_us")\t$(format_s "$batch_us")\t$(format_s "$pk_us")\t$(format_s "$order_id_us")\t$(format_s "$order_created_us")" >> "$EXP_DIR/generated/summary.tsv"
    done
done

echo "[run] done"
echo "generated/summary.tsv"
echo "generated/query-plans.raw.md"
