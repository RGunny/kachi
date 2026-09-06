package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("KeywordExpansionPersistenceAdapter 통합 테스트")
class KeywordExpansionPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: KeywordExpansionPersistenceAdapter

    @Autowired
    private lateinit var repository: KeywordExpansionMongoRepository

    private val createdAt = AiTestFixture.NOW

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("KeywordExpansion 도메인을 MongoDB에 저장한다")
        fun saveKeywordExpansion() = runBlocking {
            val expansion = keywordExpansion()

            val saved = adapter.save(expansion)
            val found = repository.findById(saved.id.value).block()

            assertNotNull(found)
            assertEquals(saved.id.value, found.id)
            assertEquals("NVIDIA", found.keyword)
            assertEquals(listOf("AI 반도체", "GPU"), found.expandedKeywords)
            assertEquals("openrouter", found.provider)
            assertEquals("openai/gpt-4o-mini", found.model)
            assertEquals("keyword-expansion-v1", found.promptVersion)
        }

        @Test
        @DisplayName("같은 keyword, promptVersion 조합은 model이 달라도 중복 저장할 수 없다")
        fun rejectDuplicateKeywordAndPromptVersion() = runBlocking {
            adapter.save(keywordExpansion())

            assertFailsWith<DuplicateKeyException> {
                adapter.save(keywordExpansion(provider = LlmProvider.GROQ, model = "llama-3.3-70b"))
            }
        }
    }

    @Nested
    @DisplayName("findByUniqueKey()")
    inner class FindByUniqueKey {

        @Test
        @DisplayName("같은 keyword, promptVersion 조합이면 model이 달라도 기존 확장을 조회한다")
        fun findExistingExpansion() = runBlocking {
            adapter.save(keywordExpansion(provider = LlmProvider.GROQ, model = "llama-3.3-70b"))

            val found = adapter.findByUniqueKey(
                keyword = AiKeyword.of("NVIDIA"),
                promptVersion = PromptVersion.of("keyword-expansion-v1")
            )

            assertNotNull(found)
            assertEquals(listOf("AI 반도체", "GPU"), found.expandedKeywords.map { it.value })
            assertEquals("llama-3.3-70b", found.model)
        }

        @Test
        @DisplayName("기존 확장이 없으면 null을 반환한다")
        fun returnNullWhenExpansionDoesNotExist() = runBlocking {
            val found = adapter.findByUniqueKey(
                keyword = AiKeyword.of("NVIDIA"),
                promptVersion = PromptVersion.of("keyword-expansion-v1")
            )

            assertNull(found)
        }
    }

    @Nested
    @DisplayName("saveOrFindExisting()")
    inner class SaveOrFindExisting {

        @Test
        @DisplayName("중복 저장이 발생하면 기존 확장을 반환한다")
        fun returnExistingExpansionOnDuplicateSave() = runBlocking {
            val first = adapter.save(keywordExpansion())

            val second = adapter.saveOrFindExisting(keywordExpansion(provider = LlmProvider.GROQ, model = "llama-3.3-70b"))

            assertEquals(first.id, second.id)
            assertEquals(1, repository.count().block())
        }
    }

    private fun keywordExpansion(
        provider: LlmProvider = LlmProvider.OPENROUTER,
        model: String = "openai/gpt-4o-mini"
    ): KeywordExpansion {
        return KeywordExpansion.create(
            keyword = AiKeyword.of("NVIDIA"),
            expandedKeywords = listOf(
                ExpandedKeyword.of("AI 반도체"),
                ExpandedKeyword.of("GPU")
            ),
            provider = provider,
            model = model,
            requestedModel = model,
            promptVersion = PromptVersion.of("keyword-expansion-v1"),
            createdAt = createdAt
        )
    }
}
