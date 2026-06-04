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
- `newsHash` 없이 `keyword + promptVersion + model`만 unique index로 둔 경우
- `newsHash`를 포함한 현재 설계

측정 항목:

- 같은 입력을 2회 실행했을 때 저장된 `news_summaries` 개수
- 같은 입력을 2회 실행했을 때 LLM 호출 횟수
- 같은 기간에 뉴스 id 1개가 추가됐을 때 새 요약 저장 가능 여부
- unique index 충돌 발생 횟수
- 반복 실행 총 소요 시간

실험 데이터 구성:

- 대용량 테스트가 필요하면 실제 LLM API를 호출하지 않는다.
- 요약 title/content/sentiment/token usage는 고정 규칙으로 만든 dummy 값으로 채운다.
- 이 실험의 목적은 LLM 품질 측정이 아니라 `newsHash` 유무에 따른 중복 저장, index 충돌, 재실행 비용 차이를 확인하는 것이다.

실험 결과 기록:

| 조건 | 1회차 저장 수 | 2회차 누적 저장 수 | LLM 호출 수 | 뉴스 추가 후 새 요약 저장 | 비고 |
| --- | ---: | ---: | ---: | --- | --- |
| unique index 없음 | TBD | TBD | TBD | TBD | TBD |
| keyword/prompt/model unique | TBD | TBD | TBD | TBD | TBD |
| keyword/newsHash/prompt/model unique | TBD | TBD | TBD | TBD | TBD |

## 결과

- `newsHash`는 뉴스 중복 판별용 값이 아니라 뉴스 요약 입력 묶음의 fingerprint다.
- 저장 중복 방지 기준은 `keyword + newsHash + promptVersion + model`로 둔다.
- 실험 결과는 위 표에 누적하고, 실제 운영 정책은 중복 저장 차단과 idempotent 재실행 처리 요구가 확정될 때 다시 조정한다.
