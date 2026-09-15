package me.rgunny.kachi.story.config

import java.time.Clock
import java.util.function.Supplier
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.rgunny.kachi.story.application.port.inbound.outbox.RelayStoryOutboxUseCase
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPersistencePort
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPublisherPort
import me.rgunny.kachi.story.fake.FakeStoryOutboxPersistencePort
import me.rgunny.kachi.story.fake.FakeStoryOutboxPublisherPort
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.BeansException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

@DisplayName("StoryOutboxRelayConfig")
class StoryOutboxRelayConfigTest {

    @Test
    @DisplayName("발행 대상이 있으면 relay 유스케이스를 조립한다")
    fun assembleRelayWithPublishTarget() {
        relayContext { registerBean(StoryOutboxPublisherPort::class.java, Supplier { FakeStoryOutboxPublisherPort() }) }.use { context ->
            context.refresh()

            assertNotNull(context.getBean(RelayStoryOutboxUseCase::class.java))
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
            assertTrue(messages.any { it.contains(StoryOutboxRelayConfig.NO_PUBLISH_TARGET_MESSAGE) }, messages.toString())
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
                MapPropertySource("relay", mapOf("${StoryOutboxRelayProperties.PREFIX}.enabled" to "true"))
            )
            registerBean(StoryOutboxRelayProperties::class.java, Supplier { StoryTestFixture.relayProperties() })
            registerBean(StoryOutboxPersistencePort::class.java, Supplier { FakeStoryOutboxPersistencePort() })
            registerBean(Clock::class.java, Supplier { StoryTestFixture.CLOCK })
            register(StoryOutboxRelayConfig::class.java)
            customize()
        }
    }
}
