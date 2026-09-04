package me.rgunny.kachi.ai.application.port.outbound.outbox.model

import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineId
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("AiOutboxEvent")
class AiOutboxEventTest {
    private val now = AiTestFixture.NOW

    @Nested
    @DisplayName("SummaryCreated")
    inner class SummaryCreated {

        @Test
        @DisplayName("요약에서 발행할 값을 그대로 옮긴다")
        fun mapFromNewsSummary() {
            val summary = AiTestFixture.newsSummary()

            val event = SummaryCreatedEvent.from(summary)

            assertEquals(summary.id.value, event.summaryId)
            assertEquals("NVIDIA", event.keyword)
            assertEquals(summary.title, event.title)
            assertEquals(summary.content, event.content)
            assertEquals(NewsSummarySentiment.NEUTRAL, event.sentiment)
            assertEquals(1, event.sourceNewsCount)
            assertEquals(AiTestFixture.PROVIDER.code, event.provider)
            assertEquals(AiTestFixture.MODEL, event.model)
            assertEquals(AiTestFixture.NEWS_SUMMARY_PROMPT_VERSION.value, event.promptVersion)
            assertEquals(now, event.createdAt)
            assertEquals(AiOutboxEvent.CURRENT_SCHEMA_VERSION, event.schemaVersion)
        }

        @Test
        @DisplayName("요약 id가 이벤트 키이고 키워드가 파티션 키다")
        fun keysComeFromSummary() {
            val summary = AiTestFixture.newsSummary()

            val event = SummaryCreatedEvent.from(summary)

            assertEquals(AiOutboxEventType.SUMMARY_CREATED, event.type)
            assertEquals(summary.id.value.toString(), event.eventKey)
            assertEquals("NVIDIA", event.partitionKey)
        }
    }

    @Nested
    @DisplayName("KeywordQuarantined")
    inner class KeywordQuarantined {

        @Test
        @DisplayName("격리 기록에서 발행할 값을 그대로 옮긴다")
        fun mapFromQuarantine() {
            val quarantine = AiTestFixture.quarantine(consecutiveFailures = 3)

            val event = KeywordQuarantinedEvent.from(quarantine)

            assertEquals(quarantine.id.value, event.quarantineId)
            assertEquals(AiRunTargetType.NEWS_SUMMARY, event.targetType)
            assertEquals("NVIDIA", event.keyword)
            assertEquals(3, event.consecutiveFailures)
            assertEquals(AiFailureReason.UNKNOWN, event.lastFailureReason)
            assertEquals(now, event.quarantinedAt)
            assertEquals(AiOutboxEvent.CURRENT_SCHEMA_VERSION, event.schemaVersion)
        }

        @Test
        @DisplayName("이벤트 키는 기록 id와 격리 시각을 함께 쓴다")
        fun keyIncludesQuarantinedAt() {
            val quarantine = AiTestFixture.quarantine(consecutiveFailures = 3)

            val event = KeywordQuarantinedEvent.from(quarantine)

            assertEquals(AiOutboxEventType.KEYWORD_QUARANTINED, event.type)
            assertEquals("${quarantine.id.value}:${now.toEpochMilli()}", event.eventKey)
            assertEquals("NVIDIA", event.partitionKey)
        }

        @Test
        @DisplayName("격리 상태가 아닌 기록으로는 만들 수 없다")
        fun rejectNotQuarantined() {
            val tracking = AiTestFixture.quarantine(consecutiveFailures = 1)

            assertFailsWith<IllegalArgumentException> { KeywordQuarantinedEvent.from(tracking) }
        }

        @Test
        @DisplayName("격리 시각이나 마지막 실패 원인이 없으면 만들 수 없다")
        fun rejectMissingQuarantineDetails() {
            assertFailsWith<IllegalArgumentException> {
                KeywordQuarantinedEvent.from(
                    restoreQuarantined(quarantinedAt = null, lastFailureReason = AiFailureReason.UNKNOWN)
                )
            }
            assertFailsWith<IllegalArgumentException> {
                KeywordQuarantinedEvent.from(
                    restoreQuarantined(quarantinedAt = now, lastFailureReason = null)
                )
            }
        }
    }

    /**
     * 도메인 전이로는 만들 수 없는 결손 기록. 저장소에 그런 문서가 남아 있을 때 이벤트 변환이 막히는지 본다.
     */
    private fun restoreQuarantined(
        quarantinedAt: Instant?,
        lastFailureReason: AiFailureReason?
    ): KeywordQuarantine {
        return KeywordQuarantine.restore(
            id = KeywordQuarantineId.newId(),
            targetType = AiRunTargetType.NEWS_SUMMARY,
            keyword = AiTestFixture.keyword(),
            consecutiveFailures = 3,
            lastFailureReason = lastFailureReason,
            status = KeywordQuarantineStatus.QUARANTINED,
            quarantinedAt = quarantinedAt,
            releasedAt = null,
            updatedAt = now
        )
    }
}
