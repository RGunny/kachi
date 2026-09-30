package me.rgunny.kachi.collector.config

import java.time.Clock
import java.util.function.Supplier
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.rgunny.kachi.collector.application.port.inbound.outbox.RelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.port.outbound.outbox.CollectorOutboxPersistencePort
import me.rgunny.kachi.collector.application.port.outbound.outbox.CollectorOutboxPublisherPort
import me.rgunny.kachi.collector.fake.FakeCollectorOutboxPersistencePort
import me.rgunny.kachi.collector.fake.FakeCollectorOutboxPublisherPort
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.BeansException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

@DisplayName("CollectorOutboxRelayConfig")
class CollectorOutboxRelayConfigTest {

    @Test
    @DisplayName("발행 대상이 있으면 relay 유스케이스를 조립한다")
    fun assembleRelayWithPublishTarget() {
        relayContext { registerBean(CollectorOutboxPublisherPort::class.java, Supplier { FakeCollectorOutboxPublisherPort() }) }.use { context ->
            context.refresh()

            assertNotNull(context.getBean(RelayCollectorOutboxUseCase::class.java))
        }
    }

    /**
     * relay는 켰는데 보낼 곳이 없는 조합은 모순된 설정이다. 조용히 보류하면 켜져 있다고 믿는데 아무것도 나가지 않는다.
     */
    @Test
    @DisplayName("relay가 켜져 있는데 발행 대상이 없으면 원인과 조치를 적어 기동에 실패한다")
    fun failStartupWithoutPublishTarget() {
        relayContext().use { context ->
            val failure = assertFailsWith<BeansException> { context.refresh() }

            val messages = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(messages.any { it.contains(CollectorOutboxRelayConfig.NO_PUBLISH_TARGET_MESSAGE) }, messages.toString())
        }
    }

    /**
     * relay 설정을 켠 채로 그 의존성만 등록한 컨텍스트. 발행 포트는 테스트가 넣거나 뺀다.
     */
    private fun relayContext(
        customize: AnnotationConfigApplicationContext.() -> Unit = {}
    ): AnnotationConfigApplicationContext {
        return AnnotationConfigApplicationContext().apply {
            environment.propertySources.addFirst(
                MapPropertySource("relay", mapOf("${CollectorOutboxRelayProperties.PREFIX}.enabled" to "true"))
            )
            registerBean(CollectorOutboxRelayProperties::class.java, Supplier { CollectorTestFixture.relayProperties() })
            registerBean(CollectorOutboxPersistencePort::class.java, Supplier { FakeCollectorOutboxPersistencePort() })
            registerBean(Clock::class.java, Supplier { CollectorTestFixture.CLOCK })
            register(CollectorOutboxRelayConfig::class.java)
            customize()
        }
    }
}
