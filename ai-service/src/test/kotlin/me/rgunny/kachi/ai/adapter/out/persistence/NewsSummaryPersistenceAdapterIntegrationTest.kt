package me.rgunny.kachi.ai.adapter.out.persistence

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DisplayName("NewsSummaryPersistenceAdapter 통합 테스트")
class NewsSummaryPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NewsSummaryPersistenceAdapter

    @Autowired
    private lateinit var repository: NewsSummaryMongoRepository

    private val createdAt = AiTestFixture.NOW
    private val sourceNewsIds = listOf(
        UUID.fromString("018f0000-0000-7000-8000-000000000001"),
        UUID.fromString("018f0000-0000-7000-8000-000000000002")
    )

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
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
        @DisplayName("같은 keyword, newsHash, promptVersion, model 조합은 중복 저장할 수 없다")
        fun rejectDuplicateKeywordWindowPromptVersionAndModel() = runBlocking {
            adapter.save(newsSummary())

            assertFailsWith<DuplicateKeyException> {
                adapter.save(newsSummary())
            }
        }

        @Test
        @DisplayName("같은 keyword, newsHash, promptVersion, model 조합으로 기존 요약을 조회한다")
        fun findByUniqueKey() = runBlocking {
            adapter.save(newsSummary())

            val found = adapter.findByUniqueKey(
                keyword = AiKeyword.of("NVIDIA"),
                newsHash = "news-hash",
                promptVersion = PromptVersion.of("news-summary-v1"),
                model = LlmModelName.of("openai/gpt-4o-mini")
            )

            assertNotNull(found)
            assertEquals("summary title", found.title)
        }

        @Test
        @DisplayName("중복 저장이 발생하면 기존 요약을 반환한다")
        fun returnExistingSummaryOnDuplicateSave() = runBlocking {
            val first = adapter.save(newsSummary())

            val second = adapter.saveOrFindExisting(newsSummary())

            assertEquals(first.id, second.id)
            assertEquals(1, repository.count().block())
        }
    }

    private fun newsSummary(): NewsSummary {
        return NewsSummary.create(
            keyword = AiKeyword.of("NVIDIA"),
            sourceNewsIds = sourceNewsIds,
            newsHash = "news-hash",
            title = "summary title",
            content = "summary content",
            sentiment = NewsSummarySentiment.NEUTRAL,
            provider = LlmProviderName.of("openrouter"),
            model = LlmModelName.of("openai/gpt-4o-mini"),
            promptVersion = PromptVersion.of("news-summary-v1"),
            tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 5),
            createdAt = createdAt
        )
    }
}
