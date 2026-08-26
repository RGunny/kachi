package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProviderStatus
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

/**
 * 차단 상태를 들고 있는 provider들을 이름으로 찾아 보고 되돌린다.
 *
 * 상태는 한 시각 기준으로 함께 읽는다. provider마다 다른 시각으로 읽으면 목록 안에서 기준이 어긋난다.
 */
class GuardedLlmProviderAdmin(
    private val providers: List<GuardedLlmProvider>,
    private val clock: Clock
) : LlmProviderAdminPort {

    override fun statuses(): List<LlmProviderStatus> {
        val now = Instant.now(clock)

        return providers.map { it.status(now) }
    }

    override fun reset(name: LlmProviderName): Boolean {
        val provider = providers.firstOrNull { it.provider == name } ?: return false
        val before = provider.status(Instant.now(clock))

        // 수동 개입은 이후 호출 결과를 해석하는 기준이 되므로 되돌리기 직전 상태를 남긴다.
        log.warn(
            "Resetting LLM provider guard: provider={}, circuitBreakerState={}, cooldownUntil={}",
            before.provider.value,
            before.circuitBreakerState,
            before.cooldownUntil
        )

        provider.reset()

        return true
    }

    private companion object {
        val log = LoggerFactory.getLogger(GuardedLlmProviderAdmin::class.java)
    }
}
