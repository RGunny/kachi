package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProviderStatus
import me.rgunny.kachi.ai.domain.llm.LlmProviderName

/**
 * 지정한 provider 상태를 돌려주는 admin 포트 fake.
 *
 * [resetNames]에 되돌리기 요청을 순서대로 기록한다. 없는 이름을 요청하면 false를 돌려준다.
 */
class FakeLlmProviderAdminPort : LlmProviderAdminPort {
    var statuses: List<LlmProviderStatus> = emptyList()
    val resetNames: MutableList<LlmProviderName> = mutableListOf()

    override fun statuses(): List<LlmProviderStatus> = statuses

    override fun reset(name: LlmProviderName): Boolean {
        resetNames.add(name)

        return statuses.any { it.provider == name }
    }
}
