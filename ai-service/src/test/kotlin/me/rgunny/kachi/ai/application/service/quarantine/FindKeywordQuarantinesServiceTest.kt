package me.rgunny.kachi.ai.application.service.quarantine

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindKeywordQuarantinesQuery
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fake.FakeKeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals

@DisplayName("FindKeywordQuarantinesService")
class FindKeywordQuarantinesServiceTest {
    private val quarantinePersistencePort = FakeKeywordQuarantinePersistencePort()
    private val service = FindKeywordQuarantinesService(quarantinePersistencePort)

    @ParameterizedTest
    @EnumSource(KeywordQuarantineStatus::class)
    @DisplayName("상태 필터를 걸면 그 상태의 기록만 돌려준다")
    fun filterByStatus(status: KeywordQuarantineStatus) = runBlocking {
        KeywordQuarantineStatus.entries.forEach { quarantinePersistencePort.quarantines += quarantineWith(it) }

        val result = service.find(FindKeywordQuarantinesQuery(targetType = null, status = status))

        assertEquals(listOf(status), result.quarantines.map { it.status })
    }

    @Test
    @DisplayName("필터가 없으면 두 대상 종류의 기록을 모두 합쳐 돌려준다")
    fun findAllTargetTypesWithoutFilter() = runBlocking {
        quarantinePersistencePort.quarantines += quarantineWith(
            status = KeywordQuarantineStatus.QUARANTINED,
            targetType = AiRunTargetType.NEWS_SUMMARY
        )
        quarantinePersistencePort.quarantines += quarantineWith(
            status = KeywordQuarantineStatus.TRACKING,
            targetType = AiRunTargetType.KEYWORD_EXPANSION
        )

        val result = service.find(FindKeywordQuarantinesQuery(targetType = null, status = null))

        assertEquals(
            setOf(AiRunTargetType.NEWS_SUMMARY, AiRunTargetType.KEYWORD_EXPANSION),
            result.quarantines.map { it.targetType }.toSet()
        )
    }

    @Test
    @DisplayName("대상 종류를 지정하면 그 종류의 기록만 돌려준다")
    fun filterByTargetType() = runBlocking {
        quarantinePersistencePort.quarantines += quarantineWith(
            status = KeywordQuarantineStatus.QUARANTINED,
            targetType = AiRunTargetType.NEWS_SUMMARY
        )
        quarantinePersistencePort.quarantines += quarantineWith(
            status = KeywordQuarantineStatus.QUARANTINED,
            targetType = AiRunTargetType.KEYWORD_EXPANSION
        )

        val result = service.find(
            FindKeywordQuarantinesQuery(targetType = AiRunTargetType.NEWS_SUMMARY, status = null)
        )

        assertEquals(listOf(AiRunTargetType.NEWS_SUMMARY), result.quarantines.map { it.targetType })
    }

    private fun quarantineWith(
        status: KeywordQuarantineStatus,
        targetType: AiRunTargetType = AiRunTargetType.NEWS_SUMMARY
    ): KeywordQuarantine {
        val keyword = AiKeyword.of("${targetType.name}-${status.name}")

        return when (status) {
            KeywordQuarantineStatus.TRACKING ->
                AiTestFixture.quarantine(keyword = keyword, consecutiveFailures = 1, targetType = targetType)

            KeywordQuarantineStatus.QUARANTINED ->
                quarantined(keyword, targetType)

            KeywordQuarantineStatus.RELEASED ->
                quarantined(keyword, targetType).release(AiTestFixture.NOW)
        }
    }

    private fun quarantined(keyword: AiKeyword, targetType: AiRunTargetType): KeywordQuarantine {
        return AiTestFixture.quarantine(
            keyword = keyword,
            consecutiveFailures = AiTestFixture.DEFAULT_QUARANTINE_FAILURE_THRESHOLD,
            targetType = targetType
        )
    }
}
