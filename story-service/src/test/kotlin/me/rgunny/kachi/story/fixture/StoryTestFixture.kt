package me.rgunny.kachi.story.fixture

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import me.rgunny.kachi.story.adapter.outbound.lock.InMemoryExecutionLockAdapter
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.domain.ArticleLanguage
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.Embedding
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.LinkDecision
import me.rgunny.kachi.story.domain.NewStoryLinkDecision
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryKeyword
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxClaim
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxRetryPolicy
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus

/**
 * story-service 테스트가 공유하는 고정 시간과 도메인 픽스처.
 */
object StoryTestFixture {
    val NOW: Instant = Instant.parse("2026-05-30T00:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    /**
     * 이 인스턴스 안에서만 유효한 실행 lock.
     *
     * 테스트마다 새로 만들어 앞선 테스트가 쥔 lock이 다음 테스트에 남지 않게 한다.
     */
    fun executionLock(): ExecutionLockPort = InMemoryExecutionLockAdapter(CLOCK)

    // ---- outbox ----

    /** story id와 기사 id를 이은 eventKey. */
    const val OUTBOX_EVENT_KEY: String = "018f0000-0000-7000-8000-000000000001:018f0000-0000-7000-8000-000000000009"

    /** 발행 순서의 단위인 story id. */
    const val OUTBOX_PARTITION_KEY: String = "018f0000-0000-7000-8000-000000000001"
    const val OUTBOX_PAYLOAD: String = """{"schemaVersion":1,"storyId":"018f0000-0000-7000-8000-000000000001","newsId":"018f0000-0000-7000-8000-000000000009"}"""
    const val RELAY_PUBLISHER_ID: String = "relay-test"

    fun outbox(
        eventType: StoryOutboxEventType = StoryOutboxEventType.ARTICLE_ATTACHED,
        eventKey: String = OUTBOX_EVENT_KEY,
        partitionKey: String = OUTBOX_PARTITION_KEY,
        payload: String = OUTBOX_PAYLOAD,
        now: Instant = NOW
    ): StoryOutbox {
        return StoryOutbox.create(
            eventType = eventType,
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            now = now
        )
    }

    /**
     * 상태·재시도 횟수·소유권을 지정해 복원한 outbox.
     */
    fun restoredOutbox(
        id: StoryOutboxId = StoryOutboxId.newId(),
        eventType: StoryOutboxEventType = StoryOutboxEventType.ARTICLE_ATTACHED,
        eventKey: String = OUTBOX_EVENT_KEY,
        partitionKey: String = OUTBOX_PARTITION_KEY,
        payload: String = OUTBOX_PAYLOAD,
        status: StoryOutboxStatus = StoryOutboxStatus.PENDING,
        retryCount: Int = 0,
        nextRetryAt: Instant = NOW,
        lastError: String? = null,
        publishedAt: Instant? = null,
        claim: StoryOutboxClaim? = null,
        createdAt: Instant = NOW,
        updatedAt: Instant = createdAt
    ): StoryOutbox {
        return StoryOutbox.restore(
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

    /** 재시도 정책. jitter는 기본으로 끈다. */
    fun retryPolicy(
        maxAttempts: Int = 5,
        baseDelay: Duration = Duration.ofSeconds(1),
        maxDelay: Duration = Duration.ofMinutes(1),
        multiplier: Double = 2.0,
        jitterRatio: Double = 0.0
    ): StoryOutboxRetryPolicy {
        return StoryOutboxRetryPolicy(
            maxAttempts = maxAttempts,
            baseDelay = baseDelay,
            maxDelay = maxDelay,
            multiplier = multiplier,
            jitterRatio = jitterRatio
        )
    }

    // ---- story ----

    val NEWS_ID: NewsId = NewsId.of(UUID.fromString("018f0000-0000-7000-8000-000000000009"))
    val STORY_ID: StoryId = StoryId.of(UUID.fromString("018f0000-0000-7000-8000-000000000001"))

    /**
     * 앞자리 몇 개만 준 벡터. 나머지는 0이라 코사인 값을 손으로 셀 수 있다.
     */
    fun embedding(vararg leading: Float, model: EmbeddingModel = EmbeddingModel.BGE_M3): Embedding {
        val values = FloatArray(model.dimension)
        leading.forEachIndexed { i, v -> values[i] = v }

        return Embedding.of(model, values)
    }

    fun article(
        newsId: NewsId = NEWS_ID,
        title: String = "NVIDIA 실적 발표",
        excerpt: String = "엔비디아가 2분기 실적을 발표했다",
        url: String = "https://kachi.com/news/1",
        source: ArticleSource = ArticleSource.NAVER,
        language: String = "ko",
        publishedAt: Instant = NOW.minus(Duration.ofHours(1)),
        collectedAt: Instant = NOW,
        keywords: List<String> = listOf("nvidia"),
        embedding: Embedding = embedding(1f, 0f),
        storyId: StoryId = STORY_ID,
        decision: LinkDecision = NewStoryLinkDecision(candidateStoryId = null, similarity = null),
        attachedAt: Instant = NOW
    ): StoryArticle {
        return StoryArticle.create(
            newsId = newsId,
            title = title,
            excerpt = excerpt,
            url = url,
            source = source,
            language = ArticleLanguage.of(language),
            publishedAt = publishedAt,
            collectedAt = collectedAt,
            matchedKeywords = keywords.map { StoryKeyword.of(it) },
            embedding = embedding,
            storyId = storyId,
            decision = decision,
            attachedAt = attachedAt
        )
    }

    /**
     * 첫 기사 하나로 연 story.
     */
    fun story(
        first: StoryArticle = article(),
        now: Instant = NOW,
        parentStoryId: StoryId? = null
    ): Story {
        return Story.open(first, now, parentStoryId)
    }
}
