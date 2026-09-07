package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProbeResult
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.fixture.AiTestFixture

/**
 * 지정한 모델 상태를 돌려주는 admin 포트 fake.
 *
 * [resetModels]와 [probedModels]에 요청을 순서대로 기록한다. 목록에 없는 모델은 되돌리기는 false, probe는 null이다.
 * [probeFailure]를 두면 목록에 있는 모델의 probe가 그 예외로 끝난다.
 */
class FakeLlmProviderAdminPort : LlmProviderAdminPort {
    var statuses: List<LlmModelStatus> = emptyList()
    val resetModels: MutableList<LlmModel> = mutableListOf()
    val probedModels: MutableList<LlmModel> = mutableListOf()
    var probeResult: LlmProbeResult = AiTestFixture.llmProbeResult()
    var probeFailure: Throwable? = null

    override fun statuses(): List<LlmModelStatus> = statuses

    override fun reset(model: LlmModel): Boolean {
        resetModels.add(model)

        return statuses.any { it.model == model }
    }

    override suspend fun probe(model: LlmModel): LlmProbeResult? {
        probedModels.add(model)

        if (statuses.none { it.model == model }) {
            return null
        }
        probeFailure?.let { throw it }

        return probeResult.copy(model = model)
    }
}
