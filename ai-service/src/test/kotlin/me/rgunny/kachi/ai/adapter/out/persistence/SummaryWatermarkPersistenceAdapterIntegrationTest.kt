package me.rgunny.kachi.ai.adapter.out.persistence

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("SummaryWatermarkPersistenceAdapter 통합 테스트")
class SummaryWatermarkPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: SummaryWatermarkPersistenceAdapter

    @Autowired
    private lateinit var repository: SummaryWatermarkMongoRepository

    private val position = AiTestFixture.NOW

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("watermark를 저장하고 대상 종류로 다시 조회한다")
        fun saveWatermarkAndFindByTargetType() = runBlocking {
            val watermark = SummaryWatermark.initial(
                targetType = AiRunTargetType.NEWS_SUMMARY,
                position = position,
                updatedAt = position
            )

            adapter.save(watermark)
            val found = adapter.findBy(AiRunTargetType.NEWS_SUMMARY)

            assertNotNull(found)
            assertEquals(AiRunTargetType.NEWS_SUMMARY, found.targetType)
            assertEquals(position, found.position)
            assertEquals(position, found.updatedAt)
        }

        @Test
        @DisplayName("전진한 watermark는 새 문서를 만들지 않고 같은 문서를 덮어쓴다")
        fun replaceSameDocumentOnAdvance() = runBlocking {
            val advancedTo = position.plus(Duration.ofMinutes(10))
            val watermark = SummaryWatermark.initial(
                targetType = AiRunTargetType.NEWS_SUMMARY,
                position = position,
                updatedAt = position
            )
            adapter.save(watermark)

            adapter.save(assertNotNull(watermark.advanceTo(position = advancedTo, updatedAt = advancedTo)))

            assertEquals(1, repository.count().block())
            assertEquals(advancedTo, adapter.findBy(AiRunTargetType.NEWS_SUMMARY)?.position)
        }
    }

    @Nested
    @DisplayName("findBy()")
    inner class FindBy {

        @Test
        @DisplayName("watermark가 없으면 null을 반환해 최초 기동으로 처리한다")
        fun returnNullWhenWatermarkDoesNotExist() = runBlocking {
            assertNull(adapter.findBy(AiRunTargetType.NEWS_SUMMARY))
        }
    }
}
