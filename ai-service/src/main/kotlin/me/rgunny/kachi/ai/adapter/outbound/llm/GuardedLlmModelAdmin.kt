package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.domain.llm.LlmModel
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

/**
 * 차단 상태를 들고 있는 모델들을 찾아 보고 되돌린다.
 *
 * 상태는 한 시각 기준으로 함께 읽는다. 모델마다 다른 시각으로 읽으면 목록 안에서 기준이 어긋난다.
 */
class GuardedLlmModelAdmin(
    private val models: List<GuardedLlmModel>,
    private val clock: Clock
) : LlmProviderAdminPort {

    override fun statuses(): List<LlmModelStatus> {
        val now = Instant.now(clock)

        return models.map { it.status(now) }
    }

    override fun reset(model: LlmModel): Boolean {
        val guarded = models.firstOrNull { it.model == model } ?: return false
        val before = guarded.status(Instant.now(clock))

        // 수동 개입은 이후 호출 결과를 해석하는 기준이 되므로 되돌리기 직전 상태를 남긴다.
        log.warn(
            "Resetting LLM model guard: model={}, circuitBreakerState={}, cooldownUntil={}, hold={}",
            before.model.qualifiedCode,
            before.circuitBreakerState,
            before.cooldownUntil,
            before.hold
        )

        guarded.reset()

        return true
    }

    private companion object {
        val log = LoggerFactory.getLogger(GuardedLlmModelAdmin::class.java)
    }
}
