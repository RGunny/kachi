package me.rgunny.kachi.ai.application.port.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProviderStatus
import me.rgunny.kachi.ai.domain.llm.LlmProviderName

/**
 * provider별 호출 차단 상태를 보고 되돌리는 출력 포트.
 *
 * 차단은 도메인 상태가 아니라 호출 계층의 상태다.
 * 따라서 이 포트는 도메인 타입을 돌려주지 않고 상태 스냅샷만 넘긴다.
 */
interface LlmProviderAdminPort {

    fun statuses(): List<LlmProviderStatus>

    /**
     * 회로를 닫고 cooldown을 지운다. false는 없는 provider다.
     */
    fun reset(name: LlmProviderName): Boolean
}
