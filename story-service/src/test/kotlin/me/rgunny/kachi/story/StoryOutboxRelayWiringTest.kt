package me.rgunny.kachi.story

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.inbound.outbox.StoryOutboxRelayExecutor
import me.rgunny.kachi.story.adapter.inbound.scheduler.StoryOutboxRelayScheduler
import me.rgunny.kachi.story.application.port.inbound.outbox.RelayStoryOutboxUseCase
import me.rgunny.kachi.story.application.service.outbox.StoryOutboxRelayPolicy
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import me.rgunny.kachi.story.fake.FakeStoryOutboxPublisherConfig
import me.rgunny.kachi.story.fake.FakeStoryOutboxPublisherPort
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.support.StoryOutboxCollection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles

/**
 * relay를 켠 컨텍스트가 실제로 조립되는지 확인한다.
 *
 * test 프로파일의 기본값은 relay와 events 둘 다 꺼짐이고, 이 테스트는 properties로 relay만 켠다.
 * events는 꺼진 채라 Kafka 발행 어댑터가 없으므로 발행 포트 자리에 fake를 넣고 한 tick을 끝까지 돌린다.
 * 검증 대상은 relay 빈의 조립과 tick의 완주이지 broker 발행이 아니다. broker 발행은 relay Kafka 통합 테스트가 본다.
 */
@ActiveProfiles("test")
// scheduler는 컨텍스트가 캐시에 남는 동안 계속 돌 수 있다. 다른 테스트가 넣은 행을 집어가지 않도록 첫 실행을 멀리 미룬다.
@SpringBootTest(
    properties = [
        "kachi.story.outbox.relay.enabled=true",
        "kachi.story.outbox.relay.initial-delay=1h"
    ]
)
@Import(StoryServiceTestContainersConfig::class, FakeStoryOutboxPublisherConfig::class)
class StoryOutboxRelayWiringTest {

    @Autowired
    private lateinit var relayUseCase: RelayStoryOutboxUseCase

    @Autowired
    private lateinit var executor: StoryOutboxRelayExecutor

    @Autowired
    private lateinit var scheduler: StoryOutboxRelayScheduler

    @Autowired
    private lateinit var policy: StoryOutboxRelayPolicy

    @Autowired
    private lateinit var publisherPort: FakeStoryOutboxPublisherPort

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val outboxCollection by lazy { StoryOutboxCollection(mongoTemplate) }

    @BeforeEach
    fun clearOutbox() {
        outboxCollection.clear()
        publisherPort.published.clear()
    }

    @Test
    @DisplayName("relay를 켜면 유스케이스와 실행 경로가 함께 조립된다")
    fun wireRelayBeans() {
        assertNotNull(relayUseCase)
        assertNotNull(executor)
        assertNotNull(scheduler)
    }

    @Test
    @DisplayName("relay 실행 정책은 운영 yaml 값으로 조립된다")
    fun bindProductionRelayPolicy() {
        assertEquals(50, policy.batchSize)
        assertEquals(Duration.ofSeconds(60), policy.publishingVisibilityTimeout)
        assertEquals(5, policy.retryPolicy.maxAttempts)
        assertEquals(Duration.ofSeconds(1), policy.retryPolicy.baseDelay)
        assertEquals(Duration.ofMinutes(1), policy.retryPolicy.maxDelay)
        assertEquals(2.0, policy.retryPolicy.multiplier)
        // 발행자 이름은 인스턴스마다 달라야 소유권을 구분할 수 있어 호스트 이름에서 온다.
        assertTrue(policy.publisherId.isNotBlank())
    }

    @Test
    @DisplayName("한 tick이 저장된 이벤트를 발행하고 발행 완료로 바꾼다")
    fun publishStoredOutboxInOneTick() = runBlocking {
        outboxCollection.insert(StoryTestFixture.outbox())

        val result = relayUseCase.relay()

        assertEquals(1, result.published)
        assertEquals(1, publisherPort.published.size)
        assertEquals(StoryOutboxStatus.PUBLISHED.name, outboxCollection.findAll().single().status)
    }
}
