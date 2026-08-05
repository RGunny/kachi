# 019. notification 운영 모니터링 및 관측성 설계

## 결정

notification의 1차 관측성은 Prometheus scrape, Micrometer business metric, Grafana dashboard로 구성한다.

```text
notification-service ─┐
                      ├─ /actuator/prometheus ─> Prometheus ─> Grafana
notification-worker ──┘
```

- Spring Boot Actuator의 `/actuator/prometheus`를 Prometheus가 scrape한다.
- business metric은 runtime adapter가 Micrometer로 기록한다.
- `notification-core`는 Spring/Micrometer에 의존하지 않는다. core use case 결과를 service/worker adapter가 metric으로 변환한다.
- Grafana는 Prometheus를 datasource로 사용하며, notification 흐름의 처리량과 실패 결과를 표시한다.
- trace와 구조화 log 수집은 이 구성에 포함하지 않는다.

## 관측 대상

알림이 요청부터 최종 발송 또는 운영 보관까지 어느 단계에 있는지 확인한다.

```text
request
  -> outbox publish
  -> dispatch
  -> sender
  -> DLT persist
```

| 단계 | 확인할 질문 | metric |
| --- | --- | --- |
| request | 요청이 접수됐는가, 중복·잘못된 요청·실패가 있었는가 | `kachi_notification_request_total` |
| outbox publish | DB에 저장된 dispatch가 Kafka로 발행됐는가 | `kachi_notification_outbox_publish_total` |
| dispatch | worker가 발송을 `SENT`, `RETRY_WAIT`, `DEAD` 중 어디로 처리했는가 | `kachi_notification_dispatch_total` |
| sender | 채널 sender가 성공·일시 실패·영구 실패·rate limit 중 무엇을 반환했는가 | `kachi_notification_sender_total` |
| DLT persist | 재시도 종료 메시지가 운영 저장소에 보관됐는가 | `kachi_notification_dlt_persist_total` |
| stale processing recovery | 처리 중 멈춘 notification을 회수했는가 | `kachi_notification_processing_recovery_total` |

처리 시간은 request, outbox publish, dispatch, sender, stale processing recovery에 Timer로 기록한다. 1차 Grafana dashboard는 흐름별 처리량과 결과를 우선 표시하고, latency panel은 실제 SLO가 정해질 때 추가한다.

## metric 기록 방식

각 runtime adapter는 core 결과를 고정된 metric 이름과 tag 값으로 변환한다.

| 실행 위치 | recorder | 기록 시점 |
| --- | --- | --- |
| notification-service HTTP/Kafka inbound adapter | `NotificationServiceMetrics` | request accepted, duplicated, invalid, failed |
| outbox publish scheduler | `NotificationServiceMetrics` | publish 단건 결과와 scheduler tick 시간 |
| notification-worker Kafka listener | `NotificationWorkerMetrics` | dispatch 결과와 listener 처리 시간 |
| sender adapter | `NotificationWorkerMetrics` | channel sender 결과와 호출 시간 |
| DLT listener | `NotificationWorkerMetrics` | MongoDB DLT 저장 성공 또는 실패 |
| stale processing recovery scheduler | `NotificationWorkerMetrics` | 회수 결과와 scheduler tick 시간 |

metric 이름과 tag는 runtime module의 metric contract에 정의한다. adapter는 임의 문자열 대신 recorder method를 호출한다.

## metric 계약

Prometheus에서는 Micrometer 이름의 점(`.`)이 underscore(`_`)로 변환되고 Counter에는 `_total`, Timer에는 `_seconds` suffix가 붙는다.

| metric | type | tag | 값 |
| --- | --- | --- | --- |
| `kachi_notification_request_total` | Counter | `source`, `channel`, `result` | source: `http`, `kafka`; result: `accepted`, `duplicated`, `invalid`, `failed` |
| `kachi_notification_outbox_publish_total` | Counter | `result` | `published`, `failed` |
| `kachi_notification_dispatch_total` | Counter | `channel`, `status`, `classification`, `result` | result: `sent`, `retry_wait`, `dead`, `duplicated`, `unexpected_status`, `invalid_payload`, `not_ready`, `failed` |
| `kachi_notification_sender_total` | Counter | `channel`, `result`, `failure_category` | result: `success`, `rate_limited`, `transient_failure`, `permanent_failure`, `unexpected` |
| `kachi_notification_dlt_persist_total` | Counter | `result` | `persisted`, `persist_failed` |
| `kachi_notification_processing_recovery_total` | Counter | `result` | `retry_wait`, `dead`, `skipped` |

`notificationId`, `requestId`, `recipient`, error message, 외부 API 세부 error code는 metric tag로 사용하지 않는다. 이 값들은 series 수를 무제한으로 늘리므로, 단건 분석이 필요하면 log 또는 이후 trace에서 사용한다.

## 수집과 표시

Prometheus는 15초마다 아래 endpoint를 scrape한다.

| job | target | endpoint |
| --- | --- | --- |
| `notification-service` | `host.docker.internal:8084` | `/actuator/prometheus` |
| `notification-worker` | `host.docker.internal:8085` | `/actuator/prometheus` |

Grafana는 Docker network 안에서 `http://prometheus:9090` datasource를 사용한다. local 환경에서 브라우저는 Grafana `http://localhost:3000`, Prometheus `http://localhost:9094`로 접속한다.

현재 `Notification Overview` dashboard는 다음 패널을 제공한다.

- request 결과: source/channel/result별 초당 처리량
- outbox publish 결과: published/failed 초당 처리량
- dispatch 결과: channel/result별 초당 처리량
- sender 결과: channel/result별 초당 처리량
- DLT persist 결과: persisted/persist_failed 초당 처리량

Grafana dashboard JSON은 UI 상태를 포함하는 표준 export 형식이다. 각 panel의 PromQL은 같은 파일의 `targets[].expr`에 정의한다.

## 범위와 다음 확장

1차는 notification 업무 흐름의 결과와 처리 시간을 관측한다. backlog, oldest age, Kafka lag, JVM/host resource, Alertmanager, trace, 구조화 log는 별도 요구가 생길 때 추가한다.

상태별 backlog gauge를 추가할 때는 Prometheus scrape마다 MongoDB를 조회하지 않는다. scheduler 또는 별도 refresh 작업이 상태 count와 oldest age를 주기적으로 계산하고, Prometheus는 마지막 계산값만 읽는다.

## 로컬 파일

| 파일 | 역할 |
| --- | --- |
| `infra/docker-compose.observability.yml` | Prometheus와 Grafana local 실행 |
| `infra/prometheus/prometheus.yml` | scrape job과 target 설정 |
| `infra/grafana/provisioning/datasources/datasources.yaml` | Prometheus datasource 자동 등록 |
| `infra/grafana/provisioning/dashboards/dashboards.yaml` | dashboard JSON 파일 자동 등록 |
| `infra/grafana/dashboards/notification-overview.json` | Notification Overview panel과 PromQL |

포트와 local network 경계는 [포트 구성](../포트-구성.md)을 따른다.
