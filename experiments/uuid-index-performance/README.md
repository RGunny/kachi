# UUID Index Performance Experiment

## 1. 실험 설계

Kachi 프로젝트에서 aggregate ID 값을 DB에 어떤 형태로 저장할지 결정하기 위한 실험이다. 결론은 현재 구현을 설명하는 것이 아니라, 실험 결과에 따라 Kachi에서 채택할 ID 저장 방식을 정하기 위한 것이다.

> 본 실험은 실험 설계 이후 Codex(GPT-5)를 통해 실험 코드 작성과 결과 정리를 진행하였다.

### 비교 대상

| 실험군 | DB 저장 타입 | 예시 | 장점 | 단점 |
|---|---|---|---|---|
| `bigint` | `BIGINT AUTO_INCREMENT` | `100000` | 가장 작고 빠른 기준선, join/FK에 유리 | DB 생성 의존, 외부 노출 시 추측 가능 |
| `bin16_uuid_v7` | `BINARY(16)` UUID v7 | `0x019E47...` | 애플리케이션 ID 생성 가능, 시간 정렬 가능, 문자열 UUID보다 compact | `BIGINT`보다 큼, DB에서 직접 읽기 어려움 |
| `char36_uuid_v7` | `CHAR(36)` UUID v7 | `019e47e4-3a3c-...` | 사람이 읽기 쉬움 | 저장 공간과 인덱스 비용 증가 |
| `prefix_id` | `VARCHAR(64)` prefix ID | `user_019e47e4-...` | 외부에서 타입 의미가 보임 | 가장 큼, DB PK로는 비효율적 |

### UUID v7을 후보로 둔 이유

| 이유 | 설명 |
|---|---|
| 애플리케이션 ID 생성 | DB insert 전 aggregate ID를 만들 수 있다. |
| 시간 정렬성 | UUID v4와 달리 앞쪽 byte가 시간 기반이라 InnoDB PK 정렬에 유리하다. |
| 외부 노출 안정성 | 순차 숫자 ID보다 추측이 어렵다. |
| 저장 효율 | 문자열 UUID가 아니라 `BINARY(16)`으로 저장하면 UUID의 크기 부담을 줄일 수 있다. |

### 실험 예상

| 항목 | 예상 |
|---|---|
| Storage | `bigint` < `bin16_uuid_v7` < `char36_uuid_v7` < `prefix_id` |
| Insert | `bigint`가 가장 빠르고, `bin16_uuid_v7`은 근접, 문자열 계열은 느릴 것이다. |
| PK lookup | warm cache에서는 모두 빠르고 차이가 작을 것이다. |
| 최신순 조회 | `created_at` 보조 인덱스를 타며, PK가 큰 문자열 계열이 불리할 수 있다. |

## 2. 실험 환경

| 항목 | 값 |
|---|---|
| Host | Apple Silicon MacBook Pro, RAM 32GB |
| DB | Docker `mysql:8.0` |
| DB 컨테이너 | 실험군별 독립 MySQL 컨테이너와 독립 volume |
| 컨테이너 리소스 | 각 컨테이너 `cpus: "2.0"`, `mem_limit: "2g"` |
| MySQL 주요 설정 | `innodb_buffer_pool_size=512M`, `innodb_flush_log_at_trx_commit=1`, `innodb_flush_method=O_DIRECT`, `sync_binlog=1` |
| 데이터 건수 | `100,000`, `1,000,000` |
| 데이터 생성 | 동일 seed, 동일 service 분포, 동일 created_at 분포, 동일 payload |
| 인덱스 | 모든 실험군 동일: `PRIMARY KEY(id)`, `idx_created_at(created_at desc)`, `idx_service_id(service_type, id desc)` |

실행:

```sh
cd experiments/uuid-index-performance
./scripts/run.sh
```

## 3. 측정 항목

| 측정 | 쿼리/방법 | 측정 이유 |
|---|---|---|
| Storage | `information_schema.tables`의 `data_length`, `index_length` | PK 타입이 테이블과 보조 인덱스 크기에 주는 영향 확인 |
| LOAD DATA | 동일 CSV를 `load data local infile`로 적재 | 대량 적재 시 PK/인덱스 크기의 영향 확인 |
| batch INSERT | 1,000 rows/statement | 일반 insert 경로에 가까운 쓰기 비용 확인 |
| PK lookup | `where id = ?` | aggregate 단건 조회 비용 확인 |
| ID 최신순 | `order by id desc limit 100` | UUID v7의 시간 정렬성이 PK scan에 반영되는지 확인 |
| 생성일 최신순 | `order by created_at desc limit 100` | 실제 최신순 조회에서 보조 인덱스 비용 확인 |

## 4. 실험 결과

### 100000 rows

| 실험군 | Storage total | Data | Index | LOAD DATA | batch INSERT | PK lookup | ORDER BY id | ORDER BY created_at |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `bigint` | 11.55 MB | 6.52 MB | 5.03 MB | 0.546 s | 1.031 s | 432 us | 455 us | 485 us |
| `bin16_uuid_v7` | 14.55 MB | 7.52 MB | 7.03 MB | 0.580 s | 1.167 s | 377 us | 450 us | 510 us |
| `char36_uuid_v7` | 21.66 MB | 9.56 MB | 12.09 MB | 0.796 s | 1.330 s | 373 us | 462 us | 575 us |
| `prefix_id` | 25.67 MB | 11.58 MB | 14.09 MB | 0.885 s | 1.446 s | 360 us | 440 us | 574 us |

>  PK lookup은 모든 실험군이 수백 μs 수준으로 작고 동일하게 primary key를 사용하므로, ID 타입 우열 판단보다 “모든 방식이 단건 조회에서는 충분히 빠르다”고 생각했다.

### 1000000 rows

| 실험군 | Storage total | Data | Index | LOAD DATA | batch INSERT | PK lookup | ORDER BY id | ORDER BY created_at |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `bigint` | 104.72 MB | 60.59 MB | 44.12 MB | 5.652 s | 8.550 s | 377 us | 474 us | 567 us |
| `bin16_uuid_v7` | 128.84 MB | 68.64 MB | 60.20 MB | 6.095 s | 10.226 s | 389 us | 466 us | 496 us |
| `char36_uuid_v7` | 191.23 MB | 89.78 MB | 101.45 MB | 9.174 s | 12.309 s | 440 us | 449 us | 590 us |
| `prefix_id` | 219.47 MB | 101.91 MB | 117.56 MB | 10.767 s | 13.885 s | 374 us | 454 us | 593 us |

### Query plan 요약

| 쿼리 | 기대 플랜 |
|---|---|
| PK lookup | `PRIMARY` lookup |
| `ORDER BY id DESC LIMIT 100` | `PRIMARY` reverse index scan |
| `ORDER BY created_at DESC LIMIT 100` | `idx_records_created_at` index scan |

원문 `EXPLAIN ANALYZE`는 [result-query-plans.md](./result-query-plans.md)에 남긴다.

## 5. 결론

실험 결과, Kachi 프로젝트에서는 aggregate ID 저장 방식으로 **`BINARY(16)` UUID v7**을 채택한다.

| 판단 | 결론 |
|---|---|
| `BIGINT AUTO_INCREMENT` | 저장 공간과 성능 기준선으로 가장 유리하지만, DB 생성 의존과 순차 ID 외부 노출 문제가 있다. |
| `BINARY(16)` UUID v7 | `BIGINT`보다 비용은 있지만, 분산 생성/시간 정렬/외부 노출 안정성의 장점이 있고 비용이 수용 가능하다. |
| `CHAR(36)` UUID v7 | UUID를 문자열로 저장하면 저장 공간과 인덱스 비용이 커져 채택하지 않는다. |
| prefix 문자열 ID | DB PK로는 가장 비싸므로 채택하지 않는다. ID에 `user_...` 같은 prefix를 섞기보다, 필요한 구분값은 `service_type` 같은 별도 컬럼으로 두는 편이 유리하다. |
