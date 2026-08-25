package me.rgunny.kachi.ai.application.service.quarantine

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.AiQuarantineErrorCode
import me.rgunny.kachi.ai.application.exception.KeywordQuarantineNotFoundException
import me.rgunny.kachi.ai.application.exception.KeywordQuarantineNotReleasableException
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseKeywordQuarantineCommand
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
import kotlin.test.assertFailsWith

@DisplayName("ReleaseKeywordQuarantineService")
class ReleaseKeywordQuarantineServiceTest {
    private val quarantinePersistencePort = FakeKeywordQuarantinePersistencePort()
    private val service = ReleaseKeywordQuarantineService(
        keywordQuarantinePersistencePort = quarantinePersistencePort,
        clock = AiTestFixture.CLOCK
    )

    @Test
    @DisplayName("격리된 키워드를 해제하고 연속 실패 누적을 되돌린다")
    fun releaseQuarantinedKeyword() = runBlocking {
        quarantinePersistencePort.quarantines += quarantined()

        val result = service.release(command())

        assertEquals(KeywordQuarantineStatus.RELEASED, result.quarantine.status)
        assertEquals(0, result.quarantine.consecutiveFailures)
        assertEquals(AiTestFixture.NOW, result.quarantine.releasedAt)
        assertEquals(AiTestFixture.NOW, result.releasedAt)

        val saved = quarantinePersistencePort.findByKeyword(KEYWORD)
        assertEquals(KeywordQuarantineStatus.RELEASED, saved?.status)
        assertEquals(0, saved?.consecutiveFailures)
    }

    @Test
    @DisplayName("기록이 없으면 해제할 대상이 없다고 실패한다")
    fun rejectMissingQuarantine() = runBlocking {
        val error = assertFailsWith<KeywordQuarantineNotFoundException> { service.release(command()) }

        assertEquals(AiQuarantineErrorCode.QUARANTINE_NOT_FOUND, error.errorCode)
        assertEquals(0, quarantinePersistencePort.saveCount)
    }

    @ParameterizedTest
    @EnumSource(
        value = KeywordQuarantineStatus::class,
        mode = EnumSource.Mode.EXCLUDE,
        names = ["QUARANTINED"]
    )
    @DisplayName("격리 상태가 아닌 기록은 해제하지 않는다")
    fun rejectNotQuarantinedStatus(status: KeywordQuarantineStatus) = runBlocking {
        quarantinePersistencePort.quarantines += quarantineWith(status)

        val error = assertFailsWith<KeywordQuarantineNotReleasableException> { service.release(command()) }

        assertEquals(AiQuarantineErrorCode.QUARANTINE_NOT_RELEASABLE, error.errorCode)
        assertEquals(0, quarantinePersistencePort.saveCount)
    }

    @Test
    @DisplayName("command의 대상 종류에 해당하는 기록만 해제 대상으로 찾는다")
    fun findQuarantineByCommandTargetType() = runBlocking {
        quarantinePersistencePort.quarantines += quarantined(targetType = AiRunTargetType.KEYWORD_EXPANSION)

        assertFailsWith<KeywordQuarantineNotFoundException> {
            service.release(command(targetType = AiRunTargetType.NEWS_SUMMARY))
        }

        val released = service.release(command(targetType = AiRunTargetType.KEYWORD_EXPANSION))
        assertEquals(AiRunTargetType.KEYWORD_EXPANSION, released.quarantine.targetType)
    }

    private fun command(targetType: AiRunTargetType = AiRunTargetType.NEWS_SUMMARY): ReleaseKeywordQuarantineCommand {
        return ReleaseKeywordQuarantineCommand(targetType = targetType, keyword = KEYWORD)
    }

    private fun quarantined(targetType: AiRunTargetType = AiRunTargetType.NEWS_SUMMARY): KeywordQuarantine {
        return AiTestFixture.quarantine(
            keyword = KEYWORD,
            consecutiveFailures = AiTestFixture.DEFAULT_QUARANTINE_FAILURE_THRESHOLD,
            targetType = targetType
        )
    }

    private fun quarantineWith(status: KeywordQuarantineStatus): KeywordQuarantine {
        return when (status) {
            KeywordQuarantineStatus.TRACKING -> AiTestFixture.quarantine(keyword = KEYWORD, consecutiveFailures = 1)
            KeywordQuarantineStatus.QUARANTINED -> quarantined()
            KeywordQuarantineStatus.RELEASED -> quarantined().release(AiTestFixture.NOW)
        }
    }

    private companion object {
        val KEYWORD = AiTestFixture.keyword()
    }
}
