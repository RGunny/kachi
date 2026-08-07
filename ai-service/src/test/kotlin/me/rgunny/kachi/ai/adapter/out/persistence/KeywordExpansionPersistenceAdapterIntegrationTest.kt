package me.rgunny.kachi.ai.adapter.out.persistence

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
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
        @DisplayName("같은 keyword, promptVersion, model 조합은 중복 저장할 수 없다")
        fun rejectDuplicateKeywordPromptVersionAndModel() = runBlocking {
            adapter.save(keywordExpansion())

            assertFailsWith<DuplicateKeyException> {
                adapter.save(keywordExpansion())
            }
        }
    }

    private fun keywordExpansion(): KeywordExpansion {
        return KeywordExpansion.create(
            keyword = AiKeyword.of("NVIDIA"),
            expandedKeywords = listOf(
                ExpandedKeyword.of("AI 반도체"),
                ExpandedKeyword.of("GPU")
            ),
            provider = LlmProviderName.of("openrouter"),
            model = LlmModelName.of("openai/gpt-4o-mini"),
            promptVersion = PromptVersion.of("keyword-expansion-v1"),
            createdAt = createdAt
        )
    }
}
