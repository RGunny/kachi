package me.rgunny.kachi.ai.application.port.inbound.watermark.model

import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import java.time.Instant

/**
 * 진행 지점과 조회 시점 사이의 폭.
 *
 * [lagSeconds]는 저장된 값이 아니라 조회할 때마다 계산하는 파생값이다.
 * 진행 지점이 미래로 어긋나 있으면 음수가 나오며, 그 값 자체가 어긋남의 신호이므로 0으로 자르지 않는다.
 */
data class SummaryWatermarkLag(
    val targetType: AiRunTargetType,
    val position: Instant,
    val lagSeconds: Long,
    val updatedAt: Instant
)
