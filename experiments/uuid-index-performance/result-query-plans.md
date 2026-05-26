# Query Plans

`EXPLAIN ANALYZE` 원문을 읽기 쉽게 요약한 문서다. 모든 실험군은 같은 쿼리에서 기대한 인덱스를 사용했다.

## 요약

| 쿼리 | 확인할 것 | 결과 |
|---|---|---|
| PK lookup | `PRIMARY`로 단건 조회되는가 | 모든 실험군 `Rows fetched before execution` |
| `ORDER BY id DESC LIMIT 100` | PK reverse scan이 되는가 | 모든 실험군 `PRIMARY (reverse)` index scan |
| `ORDER BY created_at DESC LIMIT 100` | `created_at` 보조 인덱스를 타는가 | 모든 실험군 `idx_records_created_at` index scan |

## 100,000 rows

### PK lookup

| 실험군 | 사용 플랜 | actual time |
|---|---|---:|
| `bigint` | `PRIMARY` 단건 조회 | `0.084..0.126 ms` |
| `bin16_uuid_v7` | `PRIMARY` 단건 조회 | `0.084..0.125 ms` |
| `char36_uuid_v7` | `PRIMARY` 단건 조회 | `0.041..0.083 ms` |
| `prefix_id` | `PRIMARY` 단건 조회 | `< 0.001 ms` |

### `ORDER BY id DESC LIMIT 100`

| 실험군 | 사용 플랜 | actual time |
|---|---|---:|
| `bigint` | `PRIMARY` reverse index scan | `0.0256..0.0537 ms` |
| `bin16_uuid_v7` | `PRIMARY` reverse index scan | `0.0205..0.0502 ms` |
| `char36_uuid_v7` | `PRIMARY` reverse index scan | `0.0259..0.0554 ms` |
| `prefix_id` | `PRIMARY` reverse index scan | `0.0217..0.0512 ms` |

### `ORDER BY created_at DESC LIMIT 100`

| 실험군 | 사용 플랜 | actual time |
|---|---|---:|
| `bigint` | `idx_records_created_at` index scan | `0.0367..0.0987 ms` |
| `bin16_uuid_v7` | `idx_records_created_at` index scan | `0.0354..0.101 ms` |
| `char36_uuid_v7` | `idx_records_created_at` index scan | `0.0402..0.133 ms` |
| `prefix_id` | `idx_records_created_at` index scan | `0.0466..0.160 ms` |

## 1,000,000 rows

### PK lookup

| 실험군 | 사용 플랜 | actual time |
|---|---|---:|
| `bigint` | `PRIMARY` 단건 조회 | `0.083..0.083 ms` |
| `bin16_uuid_v7` | `PRIMARY` 단건 조회 | `0.083..0.125 ms` |
| `char36_uuid_v7` | `PRIMARY` 단건 조회 | `0.042..0.042 ms` |
| `prefix_id` | `PRIMARY` 단건 조회 | `0.083..0.124 ms` |

### `ORDER BY id DESC LIMIT 100`

| 실험군 | 사용 플랜 | actual time |
|---|---|---:|
| `bigint` | `PRIMARY` reverse index scan | `0.0300..0.0585 ms` |
| `bin16_uuid_v7` | `PRIMARY` reverse index scan | `0.0225..0.0520 ms` |
| `char36_uuid_v7` | `PRIMARY` reverse index scan | `0.0247..0.0553 ms` |
| `prefix_id` | `PRIMARY` reverse index scan | `0.0272..0.0571 ms` |

### `ORDER BY created_at DESC LIMIT 100`

| 실험군 | 사용 플랜 | actual time |
|---|---|---:|
| `bigint` | `idx_records_created_at` index scan | `0.0377..0.101 ms` |
| `bin16_uuid_v7` | `idx_records_created_at` index scan | `0.0385..0.105 ms` |
| `char36_uuid_v7` | `idx_records_created_at` index scan | `0.0465..0.161 ms` |
| `prefix_id` | `idx_records_created_at` index scan | `0.0439..0.145 ms` |

## 해석

| 항목 | 해석 |
|---|---|
| PK lookup | 모든 방식이 PK 단건 조회에서는 같은 종류의 플랜을 탄다. 모두 매우 짧은 시간에 끝나므로 이 값만으로 ID 타입 우열을 판단하지 않는다. |
| `ORDER BY id` | 모든 방식이 `PRIMARY` reverse scan을 사용한다. `BINARY(16)` UUID v7도 byte 순서 기준으로 ID 최신순 조회가 가능하다. |
| `ORDER BY created_at` | 모든 방식이 `created_at` 보조 인덱스를 사용한다. 이번 warm cache + `LIMIT 100` 조건에서는 latency가 모두 1 ms 미만으로 비슷해, 이 쿼리만으로 ID 타입별 성능 우열은 판단하지 않는다. |
