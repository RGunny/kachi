package me.rgunny.kachi.collector

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.adapter.inbound.outbox.CollectorOutboxRelayExecutor
import me.rgunny.kachi.collector.adapter.inbound.scheduler.CollectorOutboxRelayScheduler
import me.rgunny.kachi.collector.application.port.inbound.outbox.RelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.service.outbox.CollectorOutboxRelayPolicy
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import me.rgunny.kachi.collector.fake.FakeCollectorOutboxPublisherConfig
import me.rgunny.kachi.collector.fake.FakeCollectorOutboxPublisherPort
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import me.rgunny.kachi.collector.support.CollectorOutboxCollection
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
 * test 프로파일의 기본값은 relay와 events 둘 다 꺼짐이고, 이 테스트는 properties로 relay만 켠다.
 * events는 꺼진 채라 Kafka 발행 어댑터가 없으므로 발행 포트 자리에 fake를 넣고 한 tick을 끝까지 돌린다.
 * 검증 대상은 relay 빈의 조립과 tick의 완주이지 broker 발행이 아니다. 발행 어댑터 자체는 단위 테스트가, 실 broker 발행은 단계 G의 통합 테스트가 본다.
 */
@ActiveProfiles("test")
// scheduler는 컨텍스트가 캐시에 남는 동안 계속 돌 수 있다. 다른 테스트가 넣은 행을 집어가지 않도록 첫 실행을 멀리 미룬다.
@SpringBootTest(
    properties = [
        "kachi.collector.outbox.relay.enabled=true",
        "kachi.collector.outbox.relay.initial-delay=1h"
    ]
)
@Import(CollectorServiceTestContainersConfig::class, FakeCollectorOutboxPublisherConfig::class)
class CollectorOutboxRelayWiringTest {

    @Autowired
    private lateinit var relayUseCase: RelayCollectorOutboxUseCase

    @Autowired
    private lateinit var executor: CollectorOutboxRelayExecutor

    @Autowired
    private lateinit var scheduler: CollectorOutboxRelayScheduler

    @Autowired
    private lateinit var policy: CollectorOutboxRelayPolicy

    @Autowired
    private lateinit var publisherPort: FakeCollectorOutboxPublisherPort

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val outboxCollection by lazy { CollectorOutboxCollection(mongoTemplate) }

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
        outboxCollection.insert(CollectorTestFixture.outbox(eventKey = "relay-wiring-1"))

        val result = relayUseCase.relay()

        assertEquals(1, result.published)
        assertEquals(1, publisherPort.published.size)
        assertEquals(CollectorOutboxStatus.PUBLISHED.name, outboxCollection.findAll().single().status)
    }
}
