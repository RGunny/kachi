package me.rgunny.kachi.ai.application.port.outbound.watermark

import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark

/**
 * 요약 진행 지점(watermark) 저장소 출력 포트
 *
 * 대상 종류마다 watermark는 한 건이다. 여러 건이 생기면 어느 것이 진짜 진행 지점인지 판단할 수 없다.
 */
interface SummaryWatermarkPersistencePort {

    /**
     * 저장된 진행 지점을 읽는다.
     *
     * null은 아직 아무 구간도 처리하지 않았다는 뜻이며, 호출자는 이를 최초 기동으로 보고 현재 시각 기준으로 구간을 잡는다.
     */
    suspend fun findBy(targetType: AiRunTargetType): SummaryWatermark?

    /**
     * 같은 targetType의 기존 값을 덮어쓴다.
     *
     * 옮겨도 되는 값인지는 도메인(`SummaryWatermark.advanceTo`)이 판단하고, 이 포트는 받은 값을 그대로 저장한다.
     */
    suspend fun save(watermark: SummaryWatermark): SummaryWatermark
}
