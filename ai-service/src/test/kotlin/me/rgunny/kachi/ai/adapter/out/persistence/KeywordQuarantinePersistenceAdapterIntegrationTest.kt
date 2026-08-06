package me.rgunny.kachi.ai.adapter.out.persistence

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("KeywordQuarantinePersistenceAdapter 통합 테스트")
class KeywordQuarantinePersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: KeywordQuarantinePersistenceAdapter

    @Autowired
    private lateinit var repository: KeywordQuarantineMongoRepository

    private val now = Instant.parse("2026-06-03T00:00:00Z")
    private val keyword = AiKeyword.of("NVIDIA")

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
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

    private fun track(): KeywordQuarantine {
        return KeywordQuarantine.track(
            targetType = AiRunTargetType.NEWS_SUMMARY,
            keyword = keyword,
            updatedAt = now
        )
    }
}
