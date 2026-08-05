package me.rgunny.kachi.ai.application.service

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.run.AiRunStatus
import me.rgunny.kachi.ai.fake.FakeAiRunPersistencePort
import me.rgunny.kachi.ai.fake.FakeKeywordExpansionPersistencePort
import me.rgunny.kachi.ai.fake.FakeKeywordReaderPort
import me.rgunny.kachi.ai.fake.FakeLlmProviderPort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("ExpandKeywordsService")
class ExpandKeywordsServiceTest {
    private val clock = AiTestFixture.CLOCK
    private val keywordReader = FakeKeywordReaderPort()
    private val llmProvider = FakeLlmProviderPort()
    private val keywordExpansionPersistence = FakeKeywordExpansionPersistencePort()
    private val aiRunPersistence = FakeAiRunPersistencePort()

    @Test
    @DisplayName("키워드를 확장하고 실행 기록을 성공 상태로 완료한다")
    fun expandKeywords() = runBlocking {
        val service = service()

        val result = service.expand(
            ExpandKeywordsCommand(
                keywords = listOf(AiKeyword.of("NVIDIA")),
                maxExpansionsPerKeyword = 3
            )
        )

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, result.requestedKeywords)
        assertEquals(1, result.succeededCount)
        assertEquals(0, result.failureCount)
        assertEquals(1, keywordExpansionPersistence.savedExpansions.size)
        assertEquals(
            listOf("AI 반도체", "GPU"),
            keywordExpansionPersistence.savedExpansions.first().expandedKeywords.map { it.value }
        )
        assertEquals(2, aiRunPersistence.savedRuns.size)
        assertEquals(AiTestFixture.PROVIDER, aiRunPersistence.savedRuns.last().provider)
        assertEquals(AiTestFixture.MODEL, aiRunPersistence.savedRuns.last().model)
        assertEquals(AiTestFixture.KEYWORD_EXPANSION_PROMPT_VERSION, aiRunPersistence.savedRuns.last().promptVersion)
    }

    @Test
    @DisplayName("command에 키워드가 없으면 활성 키워드를 읽어 확장한다")
    fun readActiveKeywordsWhenCommandKeywordsAreEmpty() = runBlocking {
        keywordReader.keywords = listOf(AiKeyword.of("NVIDIA"))
        val service = service()

        val result = service.expand(ExpandKeywordsCommand(keywords = emptyList()))

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, keywordReader.readCount)
        assertEquals(1, result.requestedKeywords)
    }

    @Test
    @DisplayName("일부 키워드 확장에 실패하면 부분 실패 상태로 완료한다")
    fun completeAsPartiallyFailed() = runBlocking {
        llmProvider.failedKeywords = setOf(AiKeyword.of("TESLA"))
        val service = service()

        val result = service.expand(
            ExpandKeywordsCommand(
                keywords = listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("TESLA"))
            )
        )

        assertEquals(AiRunStatus.PARTIALLY_FAILED, result.status)
        assertEquals(1, result.succeededCount)
        assertEquals(1, result.failureCount)
        assertEquals(1, keywordExpansionPersistence.savedExpansions.size)
    }

    @Test
    @DisplayName("처리할 키워드가 없으면 실패 상태로 완료한다")
    fun completeAsFailedWhenKeywordsAreEmpty() = runBlocking {
        val service = service()

        val result = service.expand(ExpandKeywordsCommand(keywords = emptyList()))

        assertEquals(AiRunStatus.FAILED, result.status)
        assertEquals(0, result.requestedKeywords)
        assertEquals(0, result.succeededCount)
        assertEquals(0, result.failureCount)
    }

    private fun service(): ExpandKeywordsService {
        return ExpandKeywordsService(
            keywordReaderPort = keywordReader,
            llmProviderPort = llmProvider,
            keywordExpansionPersistencePort = keywordExpansionPersistence,
            aiRunPersistencePort = aiRunPersistence,
            clock = clock
        )
    }
}
