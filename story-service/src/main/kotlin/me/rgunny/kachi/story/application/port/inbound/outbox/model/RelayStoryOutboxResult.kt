package me.rgunny.kachi.story.application.port.inbound.outbox.model

import java.time.Instant

/**
 * relay tick 한 번의 처리 집계.
 *
 * [processed]는 소유권을 잡아 실제로 다룬 행 수이고, 다른 인스턴스가 먼저 가져간 행은 세지 않는다.
 * [retried]는 이번에 발행하지 못해 다음 차례로 미룬 행이며,
 * [dead]에는 발행 실패와 회수 끝에 한도를 넘긴 행이 함께 들어간다.
 */
data class RelayStoryOutboxResult(
    val processed: Int,
    val published: Int,
    val retried: Int,
    val dead: Int,
    val staleRecovered: Int,
    val completedAt: Instant
)
