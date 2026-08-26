package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.application.port.inbound.outbox.RelayAiOutboxUseCase
import me.rgunny.kachi.ai.application.port.outbound.outbox.AiOutboxPublisherPort
import me.rgunny.kachi.ai.application.port.outbound.persistence.AiOutboxPersistencePort
import me.rgunny.kachi.ai.fake.FakeAiOutboxPersistencePort
import me.rgunny.kachi.ai.fake.FakeAiOutboxPublisherPort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.BeansException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import java.time.Clock
import java.util.function.Supplier
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("AiOutboxRelayConfig")
class AiOutboxRelayConfigTest {

    @Test
    @DisplayName("발행 대상이 있으면 relay 유스케이스를 조립한다")
    fun assembleRelayWithPublishTarget() {
        relayContext { registerBean(AiOutboxPublisherPort::class.java, Supplier { FakeAiOutboxPublisherPort() }) }.use { context ->
            context.refresh()

            assertNotNull(context.getBean(RelayAiOutboxUseCase::class.java))
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
            assertTrue(messages.any { it.contains(AiOutboxRelayConfig.NO_PUBLISH_TARGET_MESSAGE) }, messages.toString())
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
                MapPropertySource("relay", mapOf("${AiOutboxRelayProperties.PREFIX}.enabled" to "true"))
            )
            registerBean(AiOutboxRelayProperties::class.java, Supplier { AiTestFixture.relayProperties() })
            registerBean(AiOutboxPersistencePort::class.java, Supplier { FakeAiOutboxPersistencePort() })
            registerBean(Clock::class.java, Supplier { AiTestFixture.CLOCK })
            register(AiOutboxRelayConfig::class.java)
            customize()
        }
    }
}
