package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.AiOutboxCollection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("NewsSummaryPersistenceAdapter 통합 테스트")
class NewsSummaryPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NewsSummaryPersistenceAdapter

    @Autowired
    private lateinit var repository: NewsSummaryMongoRepository

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val outboxes: AiOutboxCollection by lazy { AiOutboxCollection(mongoTemplate) }

    private val createdAt = AiTestFixture.NOW
    private val sourceNewsIds = listOf(
        UUID.fromString("018f0000-0000-7000-8000-000000000001"),
        UUID.fromString("018f0000-0000-7000-8000-000000000002")
    )

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
        outboxes.clear()
    }

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("NewsSummary 도메인을 MongoDB에 저장한다")
        fun saveNewsSummary() = runBlocking {
            val summary = newsSummary()

            val saved = adapter.save(summary)
            val found = repository.findById(saved.id.value).block()

            assertNotNull(found)
            assertEquals(saved.id.value, found.id)
            assertEquals("NVIDIA", found.keyword)
            assertEquals(sourceNewsIds, found.sourceNewsIds)
            assertEquals("summary title", found.title)
            assertEquals("summary content", found.content)
            assertEquals(NewsSummarySentiment.NEUTRAL, found.sentiment)
            assertEquals(10, found.inputTokens)
            assertEquals(5, found.outputTokens)
        }

        @Test
        @DisplayName("같은 keyword, newsHash, promptVersion 조합은 model이 달라도 중복 저장할 수 없다")
        fun rejectDuplicateKeywordNewsHashAndPromptVersion() = runBlocking {
            adapter.save(newsSummary())

            assertFailsWith<DuplicateKeyException> {
                adapter.save(newsSummary(provider = "groq", model = "llama-3.3-70b"))
            }
        }

        @Test
        @DisplayName("같은 keyword, newsHash, promptVersion 조합이면 model이 달라도 기존 요약을 조회한다")
        fun findByUniqueKey() = runBlocking {
            adapter.save(newsSummary(provider = "groq", model = "llama-3.3-70b"))

            val found = adapter.findByUniqueKey(
                keyword = AiKeyword.of("NVIDIA"),
                newsHash = "news-hash",
                promptVersion = PromptVersion.of("news-summary-v1")
            )

            assertNotNull(found)
            assertEquals("summary title", found.title)
            assertEquals("llama-3.3-70b", found.model.value)
        }

    }

    @Nested
    @DisplayName("saveOrFindExisting()")
    inner class SaveOrFindExisting {

        @Test
        @DisplayName("요약과 발행 대기 이벤트를 함께 저장한다")
        fun saveSummaryWithOutbox() = runBlocking {
            val summary = newsSummary()

            adapter.saveOrFindExisting(summary, outbox(eventKey = summary.id.value.toString()))

            assertEquals(1, repository.count().block())
            val savedOutbox = outboxes.findAll().single()
            assertEquals(summary.id.value.toString(), savedOutbox.eventKey)
            assertEquals(AiOutboxStatus.PENDING.name, savedOutbox.status)
        }

        @Test
        @DisplayName("중복 저장이 발생하면 기존 요약을 반환하고 이벤트를 남기지 않는다")
        fun returnExistingSummaryOnDuplicateSave() = runBlocking {
            val first = adapter.save(newsSummary())
            val retried = newsSummary(provider = "groq", model = "llama-3.3-70b")

            val second = adapter.saveOrFindExisting(retried, outbox(eventKey = retried.id.value.toString()))

            assertEquals(first.id, second.id)
            assertEquals(1, repository.count().block())
            assertTrue(outboxes.findAll().isEmpty())
        }

        @Test
        @DisplayName("이벤트 키가 충돌하면 예외를 던지고 요약 저장도 되돌린다")
        fun rollbackSummaryWhenOutboxConflicts() = runBlocking {
            outboxes.insert(outbox(eventKey = "already-published"))
            val summary = newsSummary(newsHash = "another-news-hash")

            assertFailsWith<DuplicateKeyException> {
                adapter.saveOrFindExisting(summary, outbox(eventKey = "already-published"))
            }

            assertEquals(0, repository.count().block())
            assertEquals(1, outboxes.findAll().size)
        }
    }

    private fun outbox(eventKey: String): AiOutbox {
        return AiTestFixture.outbox(eventKey = eventKey, now = createdAt)
    }

    private fun newsSummary(
        provider: String = "openrouter",
        model: String = "openai/gpt-4o-mini",
        newsHash: String = "news-hash"
    ): NewsSummary {
        return NewsSummary.create(
            keyword = AiKeyword.of("NVIDIA"),
            sourceNewsIds = sourceNewsIds,
            newsHash = newsHash,
            title = "summary title",
            content = "summary content",
            sentiment = NewsSummarySentiment.NEUTRAL,
            provider = LlmProviderName.of(provider),
            model = LlmModelName.of(model),
            promptVersion = PromptVersion.of("news-summary-v1"),
            tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 5),
            createdAt = createdAt
        )
    }
}
