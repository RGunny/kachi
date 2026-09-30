package me.rgunny.kachi.ai.adapter.inbound.story

import java.time.Instant

/**
 * 이미 실행 중인 story 요약 tick의 시작 시각.
 *
 * [startedAt]은 lock 소유자가 lock을 얻은 시각이다.
 */
data class RunningAiStorySummary(
    val startedAt: Instant
)
