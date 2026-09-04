package me.rgunny.kachi.ai.application.port.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.domain.llm.LlmModel

/**
 * 모델별 호출 차단 상태를 보고 되돌리는 출력 포트.
 *
 * 차단은 도메인 상태가 아니라 호출 계층의 상태다.
 * 따라서 이 포트는 도메인 타입을 돌려주지 않고 상태 스냅샷만 넘긴다.
 */
interface LlmProviderAdminPort {

    fun statuses(): List<LlmModelStatus>

    /**
     * 회로를 닫고 cooldown과 보류를 지운다. 그 모델의 제공자 보류도 함께 푼다.
     * false는 후보에 없는 모델이다.
     */
    fun reset(model: LlmModel): Boolean
}
