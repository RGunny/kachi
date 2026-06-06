# 011. ai-service newsHash 기반 뉴스 요약 중복 방지

## 배경

`ai-service`는 `collector-service`가 저장한 뉴스를 키워드 단위로 읽고 LLM으로 요약한다.
같은 요약 작업은 scheduler 재실행, 수동 재시도, 장애 복구 과정에서 반복될 수 있다.

여기서 중복 방지 대상은 뉴스 원문이 아니다.
중복 방지 대상은 "같은 입력 뉴스 묶음으로 만든 요약 결과"다.

예를 들어 다음 두 실행은 같은 요약 입력이다.

```text
keyword = NVIDIA
from = 2026-06-01T00:00:00Z
to = 2026-06-02T00:00:00Z
sourceNewsIds = [A, B, C]
```

```text
keyword = NVIDIA
from = 2026-06-01T00:00:00Z
to = 2026-06-02T00:00:00Z
sourceNewsIds = [C, B, A]
```

조회 순서는 다르지만 요약에 사용한 뉴스 id 묶음은 같다.
반대로 같은 기간이라도 수집 뉴스가 늘어나면 다른 요약 입력이다.

```text
sourceNewsIds = [A, B, C]
sourceNewsIds = [A, B, C, D]
```

## 결정

뉴스 요약 결과에는 `newsHash`를 저장한다.

`newsHash`는 다음 값을 기준으로 계산한다.

```text
keyword
from
to
sourceNewsIds
```

계산 방식은 다음과 같다.

1. `sourceNewsIds`를 중복 제거하고 정렬한다.
2. `keyword`, `from`, `to`, 정렬된 `sourceNewsIds`를 문자열로 직렬화한다.
3. 직렬화한 문자열을 SHA-256 hex 문자열로 변환한다.

MongoDB `news_summaries` collection에는 다음 unique index를 둔다.

```text
keyword + newsHash + promptVersion + model
```

같은 뉴스 입력이라도 prompt version이나 model이 바뀌면 새 요약을 저장할 수 있다.

## 왜 hash를 쓰는가

`newsHash`는 뉴스 문장이나 제목이 비슷한지 판단하지 않는다.
이미 수집된 뉴스에는 고유 id가 있으므로, 요약 입력 동일성은 뉴스 내용 비교가 아니라 뉴스 id 묶음 비교로 판단한다.

뉴스 id 목록을 그대로 unique index에 넣지 않고 hash를 쓰는 이유는 다음과 같다.

- 요약 대상 뉴스가 20개, 50개, 100개로 늘어나도 index key를 고정 길이로 유지한다.
- 배열 필드를 unique index 기준으로 직접 쓰면서 생기는 multikey index 의미 혼란을 피한다.
- `[A, B, C]`와 `[C, B, A]`처럼 순서만 다른 같은 입력을 동일하게 처리한다.
- 중복 판별 키를 `keyword + newsHash + promptVersion + model`로 단순하게 유지한다.

## 없을 때

`newsHash`가 없으면 같은 뉴스 묶음으로 만든 요약을 식별하기 어렵다.

예상되는 문제는 다음과 같다.

- 같은 scheduler window가 반복 실행될 때 같은 요약이 여러 번 저장될 수 있다.
- 수동 재시도나 장애 복구 후 재실행에서 LLM 비용이 중복 발생한다.
- notification 단계에서 같은 요약이 여러 번 발송 후보가 될 수 있다.
- 중복을 막기 위해 `keyword + from + to`만 쓰면, 같은 기간에 수집 뉴스가 추가된 경우 새 요약을 저장하지 못한다.

## 있을 때

`newsHash`가 있으면 같은 입력 뉴스 묶음은 같은 hash를 갖는다.

예상되는 효과는 다음과 같다.

- 같은 입력 요약의 중복 저장을 unique index로 차단한다.
- 같은 기간이라도 뉴스 id 묶음이 달라지면 새 요약을 저장할 수 있다.
- prompt/model 변경에 따른 새 요약 생성을 허용한다.
- 중복 판별 기준이 데이터베이스 index로 명확하게 드러난다.

## 실험 계획

사전 설계 기준으로는 `newsHash`를 도입한다.
다만 실제 효과는 local 또는 실험 환경에서 전/후 비교값을 남긴다.

비교 대상:

- `newsHash` 없이 unique index를 두지 않은 경우
- `sourceNewsIds` 배열을 unique index에 직접 포함하는 경우
- 정렬/중복 제거한 `sourceNewsIdsCanonical` 문자열을 unique index에 포함하는 경우
- `newsHash`를 포함한 현재 설계

반려한 비교안:

- `keyword + promptVersion + model` unique는 같은 키워드의 다른 기간, 다른 뉴스 묶음을 모두 같은 요약으로 취급한다.
- 이 설계는 요구사항을 만족하지 못하므로 성능 실험 대상이 아니라 설계 검토 단계에서 제외한다.

측정 항목:

- 같은 입력을 2회 실행했을 때 저장된 `news_summaries` 개수
- 같은 입력을 2회 실행했을 때 LLM 호출 횟수
- 같은 기간에 뉴스 id 1개가 추가됐을 때 새 요약 저장 가능 여부
- unique index 충돌 발생 횟수
- 반복 실행 총 소요 시간

실험 데이터 구성:

- 대용량 테스트가 필요하면 실제 LLM API를 호출하지 않는다.
- 요약 title/content/sentiment/token usage는 고정 규칙으로 만든 dummy 값으로 채운다.
- 이 실험의 목적은 LLM 품질 측정이 아니라 같은 요약 입력을 MongoDB unique key로 표현하는 방식의 기능 차이와 저장 비용 차이를 확인하는 것이다.

실험 결과 기록:

실제 LLM raw 수집:

- 키워드: `브로드컴 실적`, `SPACE-X IPO`
- provider별 호출 횟수: 각 2회
- 대용량 데이터는 실제 raw seed를 재사용해서 생성했다.

100k 결과:

| 조건 | 1회차 저장 | 1회차 ms | 2회차 저장 | 2회차 실패 | 2회차 ms | 뉴스 추가 저장 | 뉴스 추가 실패 | 뉴스 추가 ms | 최종 수 | storage size | index size |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| unique index 없음 | 100000 | 4000 | 100000 | 0 | 4000 | 100000 | 0 | 5000 | 300000 | 81772544 | 12283904 |
| sourceNewsIds 배열 unique | 100000 | 6000 | 0 | 100000 | 10000 | 0 | 100000 | 9000 | 100000 | 28528640 | 25923584 |
| sourceNewsIdsCanonical unique | 100000 | 5000 | 0 | 100000 | 10000 | 100000 | 0 | 6000 | 200000 | 57483264 | 39624704 |
| newsHash unique | 100000 | 5000 | 0 | 100000 | 9000 | 100000 | 0 | 5000 | 200000 | 57352192 | 45322240 |

1M 결과:

| 조건 | 1회차 저장 | 1회차 ms | 2회차 저장 | 2회차 실패 | 2회차 ms | 뉴스 추가 저장 | 뉴스 추가 실패 | 뉴스 추가 ms | 최종 수 | storage size | index size |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| unique index 없음 | 1000000 | 40000 | 1000000 | 0 | 43000 | 1000000 | 0 | 43000 | 3000000 | 895213568 | 190128128 |
| sourceNewsIds 배열 unique | 1000000 | 64000 | 0 | 1000000 | 99000 | 0 | 1000000 | 102000 | 1000000 | 288178176 | 511332352 |
| sourceNewsIdsCanonical unique | 1000000 | 50000 | 0 | 1000000 | 97000 | 1000000 | 0 | 59000 | 2000000 | 608735232 | 815042560 |
| newsHash unique | 1000000 | 49000 | 0 | 1000000 | 95000 | 1000000 | 0 | 55000 | 2000000 | 611999744 | 570982400 |

## 실험 검증

실험은 합리적인 저장 키 후보를 기준으로 비교했다.

검증 기준:

- 같은 입력을 2회 실행했을 때 중복 저장이 발생하는가
- 같은 기간에 뉴스 id가 추가된 입력을 새 요약으로 저장할 수 있는가
- 대용량에서 저장/충돌 시간이 rows 증가에 따라 납득 가능한 방향으로 증가하는가

### 실험 목적 검증

이 실험의 목적은 뉴스 본문이 서로 비슷한지 판별하는 것이 아니다.
목적은 같은 요약 입력을 다시 저장하려고 할 때 저장소가 중복을 막을 수 있는지 확인하는 것이다.

실험에서 같은 입력은 다음 값이 모두 같은 경우다.

- `keyword`
- `from`
- `to`
- 정렬/중복 제거된 `sourceNewsIds`
- `promptVersion`
- `model`

2회차 실행에서 duplicate key가 계속 발생하는 이유는 실험이 의도적으로 1회차와 동일한 요약 입력을 다시 insert하기 때문이다.
이는 scheduler 재실행, 수동 재시도, 장애 복구 후 재처리 상황을 재현한다.
따라서 duplicate key는 실험 실패가 아니라 "`newsHash` unique index가 같은 입력 재저장을 차단했다"는 관측값이다.

반대로 뉴스 추가 실행은 같은 keyword/from/to라도 `sourceNewsIds`에 새 id가 추가된 입력이다.
이 경우 `newsHash`가 달라져야 하며, 새 요약으로 저장되어야 한다.
이 차이를 확인하기 위해 배열 직접 unique, canonical string unique, newsHash unique를 비교했다.

### 중복 정책 검증

`unique index 없음` 조건은 예상대로 같은 입력 2회차와 뉴스 추가 입력을 모두 새 문서로 저장했다.
100k에서는 최종 300k, 1M에서는 최종 3M 문서가 저장됐다.
이는 중복 요약이 계속 누적되는 baseline이다.

`sourceNewsIds` 배열 unique 조건은 비즈니스 모델과 가장 비슷해 보이지만 요구사항을 만족하지 못했다.
1회차는 모두 저장됐고 같은 입력 2회차도 모두 차단됐다.
하지만 뉴스 id가 추가된 입력도 모두 duplicate key로 차단됐다.
MongoDB 배열 index는 배열 전체를 하나의 scalar 값으로 비교하지 않고 multikey index로 동작한다.
따라서 기존 `[A, B, C]` 입력과 새 `[A, B, C, D]` 입력이 원소를 공유하면 unique index 충돌이 발생할 수 있다.
이 조건은 기능 요구사항을 만족하지 못하므로 설계 후보에서 제외한다.

`sourceNewsIdsCanonical` unique 조건은 설계 의도와 맞았다.
첫 실행은 모두 저장됐고, 같은 입력 2회차는 모두 충돌했으며, 뉴스 id가 추가된 입력은 모두 저장됐다.
다만 뉴스 id 목록을 문자열로 직접 보관하므로 요약 대상 뉴스가 많아질수록 index key가 길어진다.

`newsHash` unique 조건도 설계 의도와 맞았다.
첫 실행은 모두 저장됐고, 같은 입력 2회차는 모두 충돌했으며, 뉴스 id가 추가된 입력은 모두 저장됐다.
100k에서는 최종 200k, 1M에서는 최종 2M 문서가 저장됐다.

### 성능 검증

저장 성공 중심 조건은 rows가 10배 증가할 때 시간이 대체로 9~12배 수준으로 증가했다.

- `unique index 없음` 1회차: 100k 4초, 1M 40초
- `sourceNewsIdsCanonical` 1회차: 100k 5초, 1M 50초
- `newsHash` 1회차: 100k 5초, 1M 49초
- `newsHash` 뉴스 추가: 100k 5초, 1M 55초

이는 대량 insert 관점에서는 납득 가능한 증가다.
MongoDB는 insert할 때 `_id` index와 보조 index를 갱신한다.
unique index가 있으면 새 key가 이미 존재하는지도 확인해야 한다.
저장 성공 케이스에서는 대부분 새 index entry를 순차적으로 추가하므로 row 증가에 따라 시간이 대략 선형으로 증가하는 것이 자연스럽다.

충돌 중심 조건은 저장 성공보다 비용이 컸다.

- `sourceNewsIds` 배열 unique 2회차 충돌: 100k 10초, 1M 99초
- `sourceNewsIdsCanonical` 2회차 충돌: 100k 10초, 1M 97초
- `newsHash` 2회차 충돌: 100k 9초, 1M 95초

대량 재실행에서 duplicate key 충돌을 DB까지 보내는 방식은 비용이 크다.
충돌 케이스에서도 MongoDB는 각 문서마다 unique index key를 만들고, B-tree 계열 index에서 기존 key 존재 여부를 확인한 뒤 duplicate key error를 만들어야 한다.
문서는 저장되지 않더라도 "확인하고 실패 처리하는 비용"은 row 수만큼 발생한다.
따라서 운영 구현에서는 unique index만 믿고 매번 insert를 시도하기보다,
가능하면 `keyword + newsHash + promptVersion + model` 기준으로 선조회하거나 upsert/idempotent 저장 정책을 추가 검토한다.

MongoDB의 일반적인 query plan은 `find`/aggregation 쿼리 최적화 결과를 보는 도구다.
이번 실험처럼 `mongoimport`/bulk insert 중 unique index가 충돌을 판정하는 과정은 일반 조회 쿼리 plan처럼 직접 나오지 않는다.
대신 `collStats`의 count/storageSize/indexSize, duplicate key 실패 수, 처리 시간을 비교해 저장 정책의 효과를 판단했다.
더 깊게 보려면 MongoDB profiler, serverStatus, FTDC, WiredTiger cache 지표, `$indexStats`를 별도로 수집하는 실험을 추가해야 한다고 
AI에게 제안받았지만, 해당 사전지식이 없음과 다음 개발에 소요가 너무 길어져 여기서 실험은 끝낸다.

크기 관점에서는 1M 기준 `sourceNewsIdsCanonical`의 index size가 약 815MB, `newsHash`의 index size가 약 571MB였다.
이번 데이터는 요약당 뉴스 id가 3~4개뿐인데도 canonical string index가 더 컸다.
요약 대상 뉴스 id 수가 20개, 50개, 100개로 늘어나면 canonical string key는 계속 길어지지만, `newsHash`는 SHA-256 hex 64자로 고정된다.
따라서 `newsHash`의 장점은 단순 insert 시간보다 index key 크기 예측 가능성과 장기적인 index size 억제에 있다.

### 이상값과 한계

100k에서 `newsHash` index size가 `sourceNewsIdsCanonical`보다 컸다.
하지만 1M에서는 `sourceNewsIdsCanonical` index size가 `newsHash`보다 훨씬 컸다.
100k는 초 단위 측정 오차와 WiredTiger 압축/페이지 분할 상태의 영향을 크게 받으므로 장기 경향 판단에는 1M 결과를 우선한다.

100k 시간 측정은 shell에서 초 단위로 측정한 값을 ms로 환산했다.
따라서 100k의 3~10초 구간은 세밀한 latency 비교가 아니라 대략적인 처리 시간으로만 해석한다.
1M 결과가 성능 판단에 더 적합하다.

실험은 Mongo 단일 container, local Docker 환경에서 수행했다.
실제 운영 성능은 MongoDB 리소스, 디스크, write concern, batch 크기, 네트워크 조건에 따라 달라질 수 있다.

## 결과

- `newsHash`는 뉴스 중복 판별용 값이 아니라 뉴스 요약 입력 묶음의 fingerprint다.
- 저장 중복 방지 기준은 `keyword + newsHash + promptVersion + model`로 둔다.
- 실험 결과는 위 표에 누적하고, 실제 운영 정책은 중복 저장 차단과 idempotent 재실행 처리 요구가 확정될 때 다시 조정한다.
