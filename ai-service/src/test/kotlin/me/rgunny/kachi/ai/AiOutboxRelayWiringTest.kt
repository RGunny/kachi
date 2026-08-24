package me.rgunny.kachi.ai

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.inbound.outbox.AiOutboxRelayExecutor
import me.rgunny.kachi.ai.adapter.inbound.scheduler.AiOutboxRelayScheduler
import me.rgunny.kachi.ai.application.port.inbound.outbox.RelayAiOutboxUseCase
import me.rgunny.kachi.ai.application.service.outbox.AiOutboxRelayPolicy
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import me.rgunny.kachi.ai.fake.FakeAiOutboxPublisherConfig
import me.rgunny.kachi.ai.fake.FakeAiOutboxPublisherPort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.AiOutboxCollection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * relay를 켠 컨텍스트가 실제로 조립되는지 확인한다.
 *
 * 운영에서는 broker 어댑터가 붙기 전까지 꺼져 있어 [AiServiceApplicationTest]가 "빈이 없다"를 고정한다.
 * 켰을 때 무엇이 살아나는지는 여기서만 볼 수 있으므로, 발행 어댑터 자리에 fake를 넣고 한 tick을 끝까지 돌린다.
 */
@ActiveProfiles("test")
// scheduler는 컨텍스트가 캐시에 남는 동안 계속 돌 수 있다. 다른 테스트가 넣은 행을 집어가지 않도록 첫 실행을 멀리 미룬다.
@SpringBootTest(
    properties = [
        "kachi.ai.outbox.relay.enabled=true",
        "kachi.ai.outbox.relay.initial-delay=1h"
    ]
)
@Import(AiServiceTestContainersConfig::class, FakeAiOutboxPublisherConfig::class)
class AiOutboxRelayWiringTest {

    @Autowired
    private lateinit var relayUseCase: RelayAiOutboxUseCase

    @Autowired
    private lateinit var executor: AiOutboxRelayExecutor

    @Autowired
    private lateinit var scheduler: AiOutboxRelayScheduler

    @Autowired
    private lateinit var policy: AiOutboxRelayPolicy

    @Autowired
    private lateinit var publisherPort: FakeAiOutboxPublisherPort

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val outboxCollection by lazy { AiOutboxCollection(mongoTemplate) }

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
        outboxCollection.insert(AiTestFixture.outbox(eventKey = "relay-wiring-1"))

        val result = relayUseCase.relay()

        assertEquals(1, result.published)
        assertEquals(1, publisherPort.published.size)
        assertEquals(AiOutboxStatus.PUBLISHED.name, outboxCollection.findAll().single().status)
    }
}
