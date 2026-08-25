package me.rgunny.kachi.ai

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import me.rgunny.kachi.ai.adapter.inbound.outbox.AiOutboxRelayExecutor
import me.rgunny.kachi.ai.adapter.inbound.scheduler.AiOutboxRelayScheduler
import me.rgunny.kachi.ai.adapter.outbound.llm.GuardedLlmProvider
import me.rgunny.kachi.ai.adapter.outbound.llm.RoutingLlmProvider
import me.rgunny.kachi.ai.application.port.inbound.outbox.FindAiOutboxesUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.RecoverAiOutboxUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.FindKeywordQuarantinesUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.ReleaseKeywordQuarantineUseCase
import me.rgunny.kachi.ai.application.port.inbound.watermark.FindSummaryWatermarksUseCase
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.outbox.AiOutboxEventSerializer
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.AiOutboxEvent
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.SummaryCreatedEvent
import me.rgunny.kachi.ai.application.service.outbox.AiOutboxRelayPolicy
import me.rgunny.kachi.ai.application.service.outbox.RelayAiOutboxService
import me.rgunny.kachi.ai.config.AiOutboxRelayProperties
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.reactive.TransactionalOperator
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ActiveProfiles("test")
@SpringBootTest
@Import(AiServiceTestContainersConfig::class)
class AiServiceApplicationTest {

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    private lateinit var llmProviderPort: LlmProviderPort

    @Autowired
    private lateinit var llmProviderAdminPort: LlmProviderAdminPort

    @Autowired
    private lateinit var transactionalOperator: TransactionalOperator

    @Autowired
    private lateinit var eventSerializer: AiOutboxEventSerializer

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var relayProperties: AiOutboxRelayProperties

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun contextLoads() {
    }

    @Test
    @DisplayName("도메인 저장과 이벤트 기록을 묶을 트랜잭션 경계가 등록된다")
    fun registerTransactionalOperator() {
        assertNotNull(transactionalOperator)
    }

    /**
     * 발행 계약의 형식은 컨텍스트가 조립한 JsonMapper가 결정한다.
     * 자동 설정이 모듈이나 기본값을 바꾸면 payload 형식이 조용히 달라지므로 여기서 고정한다.
     */
    @Test
    @DisplayName("컨텍스트의 serializer가 발행 계약대로 payload를 만든다")
    fun serializeOutboxEventWithContextSerializer() {
        val summary = AiTestFixture.newsSummary()
        val event = SummaryCreatedEvent.from(summary)

        val payload = jsonMapper.readValue(eventSerializer.serialize(event), Map::class.java)

        assertEquals(AiOutboxEvent.CURRENT_SCHEMA_VERSION, payload["schemaVersion"])
        assertEquals(summary.id.value.toString(), payload["summaryId"])
        assertEquals("NEUTRAL", payload["sentiment"])
        assertEquals("2026-06-03T00:00:00Z", payload["createdAt"])
    }

    /**
     * 발행 어댑터가 아직 없으므로 relay를 켜면 이벤트가 유실되거나 전부 실패로 쌓인다.
     * 켜고 끄는 스위치를 빈 생성 조건에 둔 이유가 이것이라, 꺼진 상태에서 빈이 없다는 사실 자체가 검증 대상이다.
     */
    @Test
    @DisplayName("relay가 꺼져 있으면 relay 빈이 만들어지지 않는다")
    fun skipRelayBeansWhenRelayIsDisabled() {
        assertFalse(relayProperties.enabled)

        listOf(
            RelayAiOutboxService::class.java,
            AiOutboxRelayExecutor::class.java,
            AiOutboxRelayScheduler::class.java,
            AiOutboxRelayPolicy::class.java
        ).forEach { type ->
            assertTrue(applicationContext.getBeanNamesForType(type).isEmpty(), "${type.simpleName} 빈이 없어야 합니다")
        }
    }

    /**
     * 운영 API의 진입점은 컨트롤러가 아니라 유스케이스 빈이다.
     * 컨트롤러 슬라이스는 fake로 서기 때문에 실제 빈이 없어도 통과한다.
     */
    @Test
    @DisplayName("운영 API가 쓰는 유스케이스 빈이 모두 등록된다")
    fun registerInternalApiUseCases() {
        listOf(
            FindKeywordQuarantinesUseCase::class.java,
            ReleaseKeywordQuarantineUseCase::class.java,
            FindSummaryWatermarksUseCase::class.java,
            FindAiOutboxesUseCase::class.java,
            RecoverAiOutboxUseCase::class.java
        ).forEach { type ->
            assertEquals(1, applicationContext.getBeanNamesForType(type).size, "${type.simpleName} 빈이 하나여야 합니다")
        }
    }

    @Test
    @DisplayName("provider 차단 상태 조회 포트는 router와 같은 provider 목록을 본다")
    fun shareGuardedProvidersWithAdminPort() {
        val router = assertIs<RoutingLlmProvider>(llmProviderPort)

        assertEquals(
            router.providers.map { assertIs<GuardedLlmProvider>(it).provider.value },
            llmProviderAdminPort.statuses().map { it.provider.value }
        )
    }

    @Test
    @DisplayName("provider마다 circuit breaker 인스턴스가 등록된다")
    fun registerCircuitBreakerPerProvider() {
        val names = circuitBreakerRegistry.allCircuitBreakers.map { it.name }.toSet()

        assertEquals(setOf("openrouter", "groq", "together", "cerebras", "mistral"), names)
    }

    @Test
    @DisplayName("컨텍스트의 circuit breaker 설정은 운영 yaml 값과 같다")
    fun bindProductionCircuitBreakerConfig() {
        val config = circuitBreakerRegistry.circuitBreaker("groq").circuitBreakerConfig

        assertEquals(6, config.slidingWindowSize)
        assertEquals(3, config.minimumNumberOfCalls)
        assertEquals(50f, config.failureRateThreshold)
        assertEquals(Duration.ofSeconds(8), config.slowCallDurationThreshold)
        assertEquals(80f, config.slowCallRateThreshold)
        assertEquals(2, config.permittedNumberOfCallsInHalfOpenState)
    }

    @Test
    @DisplayName("LLM provider 포트는 회로로 감싼 provider를 순회하는 router다")
    fun llmProviderPortRoutesGuardedProviders() {
        val router = assertIs<RoutingLlmProvider>(llmProviderPort)

        assertTrue(router.providers.isNotEmpty())
        router.providers.forEach { assertIs<GuardedLlmProvider>(it) }
    }
}
