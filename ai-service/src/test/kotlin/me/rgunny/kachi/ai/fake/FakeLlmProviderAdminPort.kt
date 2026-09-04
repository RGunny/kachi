package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.domain.llm.LlmModel

/**
 * 지정한 모델 상태를 돌려주는 admin 포트 fake.
 *
 * [resetModels]에 되돌리기 요청을 순서대로 기록한다. 목록에 없는 모델을 요청하면 false를 돌려준다.
 */
class FakeLlmProviderAdminPort : LlmProviderAdminPort {
    var statuses: List<LlmModelStatus> = emptyList()
    val resetModels: MutableList<LlmModel> = mutableListOf()

    override fun statuses(): List<LlmModelStatus> = statuses

    override fun reset(model: LlmModel): Boolean {
        resetModels.add(model)

        return statuses.any { it.model == model }
    }
}
