package me.rgunny.kachi.ai.application.port.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProbeResult
import me.rgunny.kachi.ai.domain.llm.LlmModel

/**
 * 모델별 호출 차단 상태 조회, 차단 해제, 모델 하나에 대한 실제 호출 확인(probe)을 맡는 출력 포트.
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

    /**
     * 지정한 모델 하나에 키워드 확장 요청을 한 번 실제로 보내 응답 여부를 확인한다.
     *
     * 후보 순회를 거치지 않지만 차단 장치는 그대로 지난다. 차단 중이면
     * [me.rgunny.kachi.ai.application.exception.LlmProviderException]으로 실패하고, 실패의 결과는 평소 호출과 같이 기록된다.
     * null은 후보에 없는 모델이다.
     */
    suspend fun probe(model: LlmModel): LlmProbeResult?
}
