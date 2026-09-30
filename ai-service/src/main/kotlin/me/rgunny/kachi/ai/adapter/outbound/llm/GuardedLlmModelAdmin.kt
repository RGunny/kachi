package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProbeResult
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModel
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import kotlin.time.measureTimedValue
import kotlin.time.toJavaDuration

/**
 * 차단 상태를 들고 있는 모델들의 상태 조회, 차단 해제, 지정한 모델 하나에 대한 실제 호출 확인(probe)을 맡는 관리자.
 *
 * 상태는 한 시각 기준으로 함께 읽는다. 모델마다 다른 시각으로 읽으면 목록 안에서 기준이 어긋난다.
 * probe는 가드를 그대로 지난다. 차단을 우회하는 경로를 두면 그 경로의 실패가 상태에 남지 않는다.
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
        val guarded = find(model) ?: return false
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

    /**
     * 걸린 시간은 벽시계가 아니라 단조 시계로 잰다. 주입된 [clock]은 판정 기준 시각이지 경과 시간 측정용이 아니다.
     */
    override suspend fun probe(model: LlmModel): LlmProbeResult? {
        val guarded = find(model) ?: return null

        // 과금 호출일 수 있는 수동 호출이라 시작을 남긴다. 결과는 예외든 응답이든 호출자에게 그대로 간다.
        log.info("Probing LLM model: model={}, billing={}", model.qualifiedCode, guarded.billing)

        val (result, latency) = measureTimedValue {
            guarded.expandKeyword(keyword = PROBE_KEYWORD, maxExpansions = PROBE_MAX_EXPANSIONS)
        }

        return LlmProbeResult(
            model = model,
            billing = guarded.billing,
            metadata = result.metadata,
            latency = latency.toJavaDuration(),
            expandedKeywords = result.expandedKeywords
        )
    }

    private fun find(model: LlmModel): GuardedLlmModel? = models.firstOrNull { it.model == model }

    private companion object {
        val log = LoggerFactory.getLogger(GuardedLlmModelAdmin::class.java)

        /** 어느 모델이든 답할 수 있는 흔한 키워드. probe의 목적은 확장 결과가 아니라 호출이 되는가다. */
        val PROBE_KEYWORD: AiKeyword = AiKeyword.of("NVIDIA")
        const val PROBE_MAX_EXPANSIONS = 3
    }
}
