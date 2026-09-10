# Qdrant 전송 방식 비교 (REST 대 gRPC)

story-service가 후보 색인에 어느 전송 방식으로 붙을지 정하기 위한 실험이다.
같은 Qdrant 컨테이너에 같은 부하를 REST(JDK `HttpClient` + Jackson)와 gRPC(공식 Java 클라이언트 `io.qdrant:client`)로 주고
지연, 처리량, 바이트를 잰다. 결과는 ADR 033의 근거다.

## 부하

시드 `20260909`로 만든 단위 벡터 20,000개(1024차원)와 질의 2,000개다. 두 전송 방식이 같은 데이터를 받는다.

| 항목 | 값 |
| --- | --- |
| 점 | 20,000개, 1024차원 float32, L2 정규화 |
| payload | `storyId`(story 2,000개에 10건씩), `collectedAt`(최근 96시간에 고르게), `language`(ko 7 대 en 3) |
| upsert | 배치 100점씩 200회, `wait=true` |
| 질의 | 2,000개. 넣은 벡터에 표준편차 0.02 잡음을 더해 결과가 비지 않게 한다 |
| search | top-10, `collectedAt >= now-72h` 필터, payload는 `storyId`만 |
| 반복 | 조건마다 예열 1회 뒤 3회. 반복마다 컬렉션을 다시 만든다 |
| 조건 | 전송 방식 둘 × payload index 유무 둘 |
| 순서 | 회차마다 두 전송 방식의 순서를 바꾼다. 항상 같은 순서면 뒤에 오는 쪽이 덥혀진 상태에서 재게 된다 |

## 클래스

| 클래스 | 책임 |
| --- | --- |
| `Main` | 인자 `bench`(전체)·`report`(표만 다시) |
| `Settings` | 주소와 부하 크기. 포트는 실험 compose와 같은 환경변수에서 읽는다 |
| `Vectors` | 시드 고정 점·질의 생성 |
| `Transport` | 전송 방식 계약. `recreateCollection`·`upsert`·`search` |
| `RestTransport` | `PUT /collections/{name}/points?wait=true`, `POST /collections/{name}/points/query` |
| `GrpcTransport` | `upsert`·`query` stub 호출. 응답 메시지 전체를 받아 크기를 잰다 |
| `Bench` | 조건마다 예열 뒤 반복 측정 |
| `NetCounters` | Qdrant 컨테이너 `eth0`의 누적 바이트를 `docker exec`으로 읽는다 |
| `Environment` | 측정 환경 수집. Docker·Qdrant·JDK 버전과 CPU·메모리 |
| `Report` | `results/transport.md` |

바이트는 두 지표다. 요청·응답 본문은 직렬화된 논리 크기이고 HTTP 헤더와 HTTP/2 framing이 빠져 있다.
컨테이너 수신·송신은 전선 위의 양이라 프로토콜 오버헤드가 들어 있다.

## 실행

```sh
./scripts/run.sh up        # 실험용 Qdrant (6343 REST, 6344 gRPC)
./scripts/run.sh bench     # results/transport.jsonl, environment.jsonl, transport.md
./scripts/run.sh report    # 기존 jsonl로 표만 다시 만든다
./scripts/run.sh down      # 컨테이너와 볼륨 삭제
```

부하를 줄여 빨리 확인할 때는 `bench --points 1000 --queries 100 --repeats 1`이다.
독립 Gradle 빌드라 루트 `./gradlew test`에 들어가지 않는다.

## 결과 (2026-09-10)

전체 실행에 5분 34초 걸렸다. 아래는 반복 3회의 중간값이고 전체 표와 실행 환경은 `results/transport.md`에 있다.
측정 환경은 `results/environment.jsonl`에 함께 남는다. Docker 서버 29.7.2, CPU 10, 메모리 11.7GiB, JDK 21.0.12 aarch64, Qdrant 1.19.1이다.

| 작업 | payload index | 전송 | p50 | p95 | 초당 | 전체 소요 | 요청 본문 | 컨테이너 수신 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| upsert 배치 100점 200회 | 없음 | rest | 32.4ms | 60.4ms | 26.4 | 7.58s | 245.3MB | 247.0MB |
| upsert 배치 100점 200회 | 없음 | grpc | 11.4ms | 26.0ms | 72.0 | 2.78s | 81.0MB | 81.7MB |
| upsert 배치 100점 200회 | 있음 | rest | 34.6ms | 57.6ms | 25.3 | 7.90s | 245.3MB | 247.1MB |
| upsert 배치 100점 200회 | 있음 | grpc | 12.3ms | 20.8ms | 74.2 | 2.70s | 81.0MB | 81.7MB |
| search top-10 2,000회 | 없음 | rest | 7.3ms | 24.7ms | 87.8 | 22.77s | 24.5MB | 25.2MB |
| search top-10 2,000회 | 없음 | grpc | 8.3ms | 26.2ms | 77.5 | 25.82s | 8.0MB | 8.3MB |
| search top-10 2,000회 | 있음 | rest | 8.8ms | 21.4ms | 94.7 | 21.11s | 24.5MB | 25.2MB |
| search top-10 2,000회 | 있음 | grpc | 8.7ms | 17.3ms | 102.7 | 19.48s | 8.0MB | 8.4MB |

관찰.

- 차이는 쓰기에서 난다. 같은 20,000점을 넣는 데 REST는 7.6초, gRPC는 2.8초다.
  1024차원 float를 JSON 숫자 배열로 쓰면 점당 12.3KB이고 protobuf는 4.0KB다. 서버가 파싱할 양이 3배다.
- 검색 지연은 두 방식이 사실상 같다. p50이 7.3ms에서 8.8ms 사이에 있고 어느 쪽이 앞서는지는 조건마다 뒤집힌다.
  질의 하나가 벡터 하나라 전송량이 작고 비용은 서버의 탐색에 있다.
- `collectedAt` 범위 index는 검색 p95를 줄인다. REST는 24.7ms에서 21.4ms로 14%, gRPC는 26.2ms에서 17.3ms로 34%다.
  폭이 갈리는 것은 반복 사이 흔들림이 그만큼 크기 때문이며, 방향은 둘 다 같다.
  index 없이는 `collectedAt` 조건이 후보를 훑어야 한다는 것이 이 실험이 보이는 것이다.
- upsert는 index가 있어도 느려지지 않았다. 점 20,000개 규모에서는 색인 갱신 비용이 파싱 비용에 묻힌다.

한계.

- 같은 호스트의 컨테이너를 재므로 네트워크 지연 차이는 담기지 않는다. 서버가 다른 호스트에 있으면 바이트 3배가 지연에도 드러난다.
- 점 20,000개는 운영의 3일 창(약 900,000점)보다 작다. 검색 지연의 절대값이 아니라 두 전송 방식의 차이를 보는 실험이다.
- 요청을 순서대로 하나씩 보낸다. 동시 요청에서의 처리량은 재지 않았다.
- Qdrant 단독 compose에서 잰 값이다. 전체 인프라와 애플리케이션을 같이 띄운 상태의 메모리 적합성은 이 실험이 말하지 않는다.
- payload index 조건은 운영 컬렉션과 같은 셋(`storyId`·`collectedAt`·`language`)을 만든 상태다.
  질의가 쓰는 것은 `collectedAt` 범위 index 하나이므로 검색 차이는 그 index의 효과이고, 나머지 둘은 컬렉션 모양을 맞추려고 같이 만든다.
