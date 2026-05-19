# kachi

## 서비스 개요
관심 키워드 기반 뉴스 수집 → AI 요약 → 알림 서비스

## 기술 스택
- **Backend**: Kotlin 2.2.21 + Spring Boot 4.0.6 + Java 21
- **Frontend**: ? (Vibe Coding)
- **DB**: MySQL (user, history), MongoDB (collector, ai), Redis (캐시/JWT)
- **메시징**: Kafka (서비스 간 이벤트 드리븐)
- **인프라**: Docker Compose
- **모니터링**: Prometheus + Grafana

## MSA 구조 (멀티모듈)

| 서비스                  | 언어     | 프레임워크 | DB   | 역할                          |
|----------------------|--------|-----|------|-----------------------------|
| user-service         | Kotlin | MVC | MySQL | 인증, 키워드 관리                  |
| collector-service    | Kotlin | WebFlux | MongoDB | 뉴스/시장데이터 수집                 |
| ai-service           | Kotlin | WebFlux | MongoDB | 키워드 확장, 뉴스 요약               |
| notification-service | Kotlin | WebFlux | MySQL   | Slack/Discord/Telegram 알림   |
| history-service      | Java   | Batch | MySQL | 사용자 활동/알림/요약 이력 적재 및 통계 집계  |

## 이벤트 흐름
```
user(키워드 등록) → ai(키워드 확장) → collector(뉴스 수집) → ai(요약) → notification(알림)
```

