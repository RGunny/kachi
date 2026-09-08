package me.rgunny.kachi.collector.fixture

import me.rgunny.kachi.collector.adapter.outbound.lock.InMemoryExecutionLockAdapter
import me.rgunny.kachi.collector.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.collector.application.port.outbound.outbox.model.NewsCollectedEvent
import me.rgunny.kachi.collector.application.service.outbox.CollectorOutboxRelayPolicy
import me.rgunny.kachi.collector.config.CollectorEventsProperties
import me.rgunny.kachi.collector.config.CollectorOutboxRelayProperties
import me.rgunny.kachi.collector.config.CollectorOutboxRetryProperties
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsExcerpt
import me.rgunny.kachi.collector.domain.NewsLanguage
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.NewsTitle
import me.rgunny.kachi.collector.domain.NewsUrl
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxClaim
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxEventType
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxRetryPolicy
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * collector-service 테스트가 공유하는 고정 시간과 도메인 픽스처.
 *
 * 테스트마다 다른 시각을 세우면 수집 시각과 조회 구간의 선후 관계를 테스트별로 다시 읽어야 한다.
 * 시나리오 고유 시각은 이 상수의 상대값([NOW].plus 등)으로 표현한다.
 */
object CollectorTestFixture {
    val NOW: Instant = Instant.parse("2026-05-30T00:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    /**
     * 이 인스턴스 안에서만 유효한 실행 lock.
     *
     * 테스트마다 새로 만들어 앞선 테스트가 쥔 lock이 다음 테스트에 남지 않게 한다.
     */
    fun executionLock(): ExecutionLockPort = InMemoryExecutionLockAdapter(CLOCK)

    const val OUTBOX_EVENT_KEY: String = "018f0000-0000-7000-8000-000000000009"
    const val OUTBOX_PAYLOAD: String = """{"schemaVersion":1,"newsId":"018f0000-0000-7000-8000-000000000009"}"""
    const val RELAY_PUBLISHER_ID: String = "relay-test"
    const val EVENT_TOPIC_NEWS_COLLECTED: String = "collector.news.collected"

    fun news(
        source: NewsSource = NewsSource.NAVER,
        title: String = "NVIDIA 실적 발표",
        excerpt: String = "엔비디아가 2분기 실적을 발표했다",
        url: String = "https://kachi.com/news/1",
        language: String = "ko",
        publishedAt: Instant = NOW.minus(Duration.ofHours(1)),
        collectedAt: Instant = NOW,
        keywords: List<String> = listOf("NVIDIA")
    ): News {
        return News.create(
            source = source,
            title = NewsTitle.of(title),
            excerpt = NewsExcerpt.of(excerpt),
            url = NewsUrl.of(url),
            language = NewsLanguage.of(language),
            publishedAt = publishedAt,
            collectedAt = collectedAt,
            matchedKeywords = keywords.map(CollectedKeyword::of)
        )
    }

    fun newsCollectedEvent(news: News = news()): NewsCollectedEvent {
        return NewsCollectedEvent.from(news)
    }

    fun outbox(
        eventType: CollectorOutboxEventType = CollectorOutboxEventType.NEWS_COLLECTED,
        eventKey: String = OUTBOX_EVENT_KEY,
        partitionKey: String = OUTBOX_EVENT_KEY,
        payload: String = OUTBOX_PAYLOAD,
        now: Instant = NOW
    ): CollectorOutbox {
        return CollectorOutbox.create(
            eventType = eventType,
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            now = now
        )
    }

    /**
     * 저장소에 있던 것처럼 상태·재시도 횟수·소유권을 지정해 복원한 outbox.
     *
     * [outbox]는 생성 직후만 만들 수 있으므로 발행 중이거나 실패가 쌓인 행은 여기서 만든다.
     * 기본값은 [outbox]와 같은 PENDING 행이다.
     */
    fun restoredOutbox(
        id: CollectorOutboxId = CollectorOutboxId.newId(),
        eventType: CollectorOutboxEventType = CollectorOutboxEventType.NEWS_COLLECTED,
        eventKey: String = OUTBOX_EVENT_KEY,
        partitionKey: String = OUTBOX_EVENT_KEY,
        payload: String = OUTBOX_PAYLOAD,
        status: CollectorOutboxStatus = CollectorOutboxStatus.PENDING,
        retryCount: Int = 0,
        nextRetryAt: Instant = NOW,
        lastError: String? = null,
        publishedAt: Instant? = null,
        claim: CollectorOutboxClaim? = null,
        createdAt: Instant = NOW,
        updatedAt: Instant = createdAt
    ): CollectorOutbox {
        return CollectorOutbox.restore(
            id = id,
            eventType = eventType,
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            status = status,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = lastError,
            publishedAt = publishedAt,
            claim = claim,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 재시도 정책. 분산값은 기본으로 끈다. 켜 두면 다음 차례 시각을 단언할 수 없다.
     */
    fun retryPolicy(
        maxAttempts: Int = 5,
        baseDelay: Duration = Duration.ofSeconds(1),
        maxDelay: Duration = Duration.ofMinutes(1),
        multiplier: Double = 2.0,
        jitterRatio: Double = 0.0
    ): CollectorOutboxRetryPolicy {
        return CollectorOutboxRetryPolicy(
            maxAttempts = maxAttempts,
            baseDelay = baseDelay,
            maxDelay = maxDelay,
            multiplier = multiplier,
            jitterRatio = jitterRatio
        )
    }

    fun relayPolicy(
        batchSize: Int = 10,
        publisherId: String = RELAY_PUBLISHER_ID,
        retryPolicy: CollectorOutboxRetryPolicy = retryPolicy(),
        publishingVisibilityTimeout: Duration = Duration.ofSeconds(60)
    ): CollectorOutboxRelayPolicy {
        return CollectorOutboxRelayPolicy(
            batchSize = batchSize,
            publisherId = publisherId,
            retryPolicy = retryPolicy,
            publishingVisibilityTimeout = publishingVisibilityTimeout
        )
    }

    fun relayProperties(
        enabled: Boolean = true,
        publisherId: String = RELAY_PUBLISHER_ID,
        fixedDelay: Duration = Duration.ofSeconds(5),
        initialDelay: Duration = Duration.ofSeconds(15),
        batchSize: Int = 50,
        publishingVisibilityTimeout: Duration = Duration.ofSeconds(60),
        retry: CollectorOutboxRetryProperties = retryProperties()
    ): CollectorOutboxRelayProperties {
        return CollectorOutboxRelayProperties(
            enabled = enabled,
            publisherId = publisherId,
            fixedDelay = fixedDelay,
            initialDelay = initialDelay,
            batchSize = batchSize,
            publishingVisibilityTimeout = publishingVisibilityTimeout,
            retry = retry
        )
    }

    fun retryProperties(
        maxAttempts: Int = 5,
        baseDelay: Duration = Duration.ofSeconds(1),
        maxDelay: Duration = Duration.ofMinutes(1),
        multiplier: Double = 2.0
    ): CollectorOutboxRetryProperties {
        return CollectorOutboxRetryProperties(
            maxAttempts = maxAttempts,
            baseDelay = baseDelay,
            maxDelay = maxDelay,
            multiplier = multiplier
        )
    }

    fun eventsProperties(
        enabled: Boolean = true,
        newsCollectedTopic: String = EVENT_TOPIC_NEWS_COLLECTED,
        retention: Duration = Duration.ofDays(30)
    ): CollectorEventsProperties {
        return CollectorEventsProperties(
            enabled = enabled,
            topics = CollectorEventsProperties.Topics(newsCollected = newsCollectedTopic),
            retention = retention
        )
    }
}
