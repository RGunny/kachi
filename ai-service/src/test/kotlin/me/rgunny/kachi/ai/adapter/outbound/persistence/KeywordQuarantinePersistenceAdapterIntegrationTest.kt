package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.AiOutboxCollection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("KeywordQuarantinePersistenceAdapter 통합 테스트")
class KeywordQuarantinePersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: KeywordQuarantinePersistenceAdapter

    @Autowired
    private lateinit var repository: KeywordQuarantineMongoRepository

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val outboxes: AiOutboxCollection by lazy { AiOutboxCollection(mongoTemplate) }

    private val now = AiTestFixture.NOW
    private val keyword = AiKeyword.of("NVIDIA")

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
        outboxes.clear()
    }

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("격리 기록을 저장하고 대상 종류로 다시 조회한다")
        fun saveQuarantineAndFindByTargetType() = runBlocking {
            val quarantined = track()
                .recordFailure(AiFailureReason.RATE_LIMITED, failureThreshold = 1, updatedAt = now)

            adapter.save(quarantined)
            val found = adapter.findAllBy(AiRunTargetType.NEWS_SUMMARY).firstOrNull()

            assertNotNull(found)
            assertEquals(keyword, found.keyword)
            assertEquals(1, found.consecutiveFailures)
            assertEquals(AiFailureReason.RATE_LIMITED, found.lastFailureReason)
            assertEquals(KeywordQuarantineStatus.QUARANTINED, found.status)
            assertEquals(now, found.quarantinedAt)
            assertTrue(found.isQuarantined)
        }

        @Test
        @DisplayName("같은 키워드의 실패 누적은 새 문서를 만들지 않고 같은 문서를 갱신한다")
        fun updateSameDocumentOnRepeatedFailure() = runBlocking {
            val tracked = track().recordFailure(AiFailureReason.TIMEOUT, failureThreshold = 3, updatedAt = now)
            adapter.save(tracked)

            adapter.save(tracked.recordFailure(AiFailureReason.TIMEOUT, failureThreshold = 3, updatedAt = now))

            assertEquals(1, repository.count().block())
            assertEquals(2, adapter.findAllBy(AiRunTargetType.NEWS_SUMMARY).first().consecutiveFailures)
        }
    }

    @Nested
    @DisplayName("saveQuarantined()")
    inner class SaveQuarantined {

        @Test
        @DisplayName("격리 전이와 발행 대기 이벤트를 함께 저장한다")
        fun saveQuarantineWithOutbox() = runBlocking {
            val quarantined = quarantined()

            adapter.saveQuarantined(quarantined, outbox(eventKey = "quarantined-1"))

            assertEquals(KeywordQuarantineStatus.QUARANTINED, adapter.findAllBy(AiRunTargetType.NEWS_SUMMARY).single().status)
            assertEquals("quarantined-1", outboxes.findAll().single().eventKey)
        }

        @Test
        @DisplayName("이벤트 키가 충돌하면 예외를 던지고 격리 전이도 되돌린다")
        fun rollbackQuarantineWhenOutboxConflicts() = runBlocking {
            val tracked = track().recordFailure(AiFailureReason.TIMEOUT, failureThreshold = 3, updatedAt = now)
            adapter.save(tracked)
            outboxes.insert(outbox(eventKey = "already-published"))

            assertFailsWith<DuplicateKeyException> {
                adapter.saveQuarantined(quarantined(), outbox(eventKey = "already-published"))
            }

            val found = adapter.findAllBy(AiRunTargetType.NEWS_SUMMARY).single()
            assertEquals(KeywordQuarantineStatus.TRACKING, found.status)
            assertEquals(1, found.consecutiveFailures)
            assertEquals(1, outboxes.findAll().size)
        }
    }

    @Nested
    @DisplayName("findAllBy()")
    inner class FindAllBy {

        @Test
        @DisplayName("다른 대상 종류의 기록은 조회하지 않는다")
        fun excludeOtherTargetType() = runBlocking {
            adapter.save(
                KeywordQuarantine.track(
                    targetType = AiRunTargetType.KEYWORD_EXPANSION,
                    keyword = keyword,
                    updatedAt = now
                ).recordFailure(AiFailureReason.UNKNOWN, failureThreshold = 3, updatedAt = now)
            )

            assertTrue(adapter.findAllBy(AiRunTargetType.NEWS_SUMMARY).isEmpty())
            assertEquals(1, adapter.findAllBy(AiRunTargetType.KEYWORD_EXPANSION).size)
        }
    }

    private fun quarantined(): KeywordQuarantine {
        return track().recordFailure(AiFailureReason.INVALID_RESPONSE, failureThreshold = 1, updatedAt = now)
    }

    private fun outbox(eventKey: String): AiOutbox {
        return AiTestFixture.outbox(
            eventType = AiOutboxEventType.KEYWORD_QUARANTINED,
            eventKey = eventKey,
            now = now
        )
    }

    private fun track(): KeywordQuarantine {
        return KeywordQuarantine.track(
            targetType = AiRunTargetType.NEWS_SUMMARY,
            keyword = keyword,
            updatedAt = now
        )
    }
}
