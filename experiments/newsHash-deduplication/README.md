# newsHash deduplication experiment

`ai-service` 뉴스 요약 저장 정책에서 같은 요약 입력을 어떻게 unique key로 표현할지 비교한다.

## 목표

- 실제 LLM raw 응답을 provider/model별로 각 2회 수집한다.
- 수집한 raw 응답을 seed로 재사용해 100k, 1M 더미 요약 데이터를 생성한다.
- MongoDB unique index 조건별로 중복 저장, 뉴스 추가 저장 가능 여부, index 크기, 저장 성능을 비교한다.

## 고정 입력

키워드:

- `브로드컴 실적`
- `SPACE-X IPO`

실제 LLM 호출 횟수:

- provider/model별 총 2회
- 각 호출은 위 두 키워드를 모두 요약 대상으로 포함한다.

대용량 실험:

- 100k
- 1M

대용량 실험은 실제 LLM API를 호출하지 않는다.
`raw/*.json`에 저장된 실제 raw 응답을 반복 재사용해서 deterministic dummy summary를 만든다.

## 비교 조건

MongoDB collection을 조건별로 분리한다.

- `news_summaries_no_unique`: unique index 없음
- `news_summaries_source_news_ids_array_unique`: `keyword + summaryFrom + summaryTo + sourceNewsIds + promptVersion + model` unique
- `news_summaries_source_news_ids_canonical_unique`: `keyword + summaryFrom + summaryTo + sourceNewsIdsCanonical + promptVersion + model` unique
- `news_summaries_news_hash_unique`: `keyword + newsHash + promptVersion + model` unique

## 측정 항목

- 1회차 저장 수
- 2회차 누적 저장 수
- 뉴스 추가 후 누적 저장 수
- dummy LLM 생성 횟수
- duplicate key 충돌 횟수
- insert 소요 시간
- collection size
- index size

## 준비

API key는 환경변수로만 주입한다. 파일에 저장하지 않는다.

```sh
export OPENROUTER_API_KEY=...
export GROQ_API_KEY=...
export TOGETHER_API_KEY=...
export CEREBRAS_API_KEY=...
export MISTRAL_API_KEY=...
```

모델명은 `.env.example`의 기본값과 같은 환경변수를 사용한다.

```sh
export KACHI_AI_OPENROUTER_MODEL=openai/gpt-4o-mini
export KACHI_AI_GROQ_MODEL=llama-3.3-70b-versatile
export KACHI_AI_TOGETHER_MODEL=meta-llama/Llama-3.3-70B-Instruct-Turbo
export KACHI_AI_CEREBRAS_MODEL=gpt-oss-120b
export KACHI_AI_MISTRAL_MODEL=mistral-small-latest
```

## 실행

MongoDB 시작:

```sh
docker compose -f experiments/newsHash-deduplication/docker-compose.yml up -d
```

실험용 Docker Compose는 `kachi-experiment` project name을 사용한다.
Docker Desktop에서는 `kachi-experiment` 묶음 아래에 `kachi-experiment-news-hash-mongo` 컨테이너로 표시된다.

기본 host port는 메인 로컬 MongoDB(`27017`)와 충돌하지 않도록 `27018`을 사용한다.
필요하면 다음처럼 바꿀 수 있다.

```sh
NEWS_HASH_EXPERIMENT_MONGO_PORT=27019 docker compose -f experiments/newsHash-deduplication/docker-compose.yml up -d
```

실제 LLM raw 수집:

```sh
java --source 21 experiments/newsHash-deduplication/scripts/CollectRawLlmSamples.java
```

100k 실험:

```sh
./experiments/newsHash-deduplication/scripts/run.sh 100000
```

1M 실험:

```sh
./experiments/newsHash-deduplication/scripts/run.sh 1000000
```

실험 인프라 정리:

```sh
docker compose -f experiments/newsHash-deduplication/docker-compose.yml down -v
```

`-v`는 실험 MongoDB volume을 함께 삭제한다.

## 결과 파일

- `raw/*.json`: provider/model별 실제 LLM raw response
- `generated/{rows}/*.jsonl`: raw response seed를 재사용해서 만든 Mongo import용 더미 데이터
- `results/*-summary.json`: 조건별 수치 결과
- `results/*-summary.md`: 사람이 읽는 결과 요약

## 해석 기준

`newsHash`는 뉴스 문장 유사도 판별값이 아니다.
같은 키워드, 같은 기간, 같은 뉴스 id 묶음으로 만든 요약 입력인지 판별하는 fingerprint다.

같은 입력 재실행은 막고, 뉴스 id 묶음이 달라진 입력은 새 요약으로 저장되는지가 핵심이다.

배열 직접 unique는 비즈니스 모델과 가장 비슷해 보이지만 MongoDB multikey unique index로 동작한다.
따라서 `sourceNewsIds=[A,B,C]` 저장 후 `sourceNewsIds=[A,B,C,D]`를 넣을 때도 기존 원소와 충돌할 수 있다.

canonical string unique와 newsHash unique는 둘 다 뉴스 id 묶음 전체를 하나의 scalar key로 비교한다.
canonical string은 원본 id 묶음을 사람이 읽을 수 있는 문자열로 보존하고, newsHash는 같은 의미를 고정 길이 key로 줄인다.
