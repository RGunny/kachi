package me.rgunny.kachi.notification.application.port.outbound.idempotency

import java.time.Duration

/**
 * 요청/dispatch 멱등 마커를 관리하는 port.
 *
 * 같은 요청/dispatch가 동시에 처리되는 것을 1차로 차단한다.
 * 최종 정합성은 DB unique key와 도메인 상태 전이 가드가 함께 보장한다.
 */
interface NotificationDeduplicationPort {

    suspend fun acquire(key: String, ttl: Duration): Boolean

    suspend fun release(key: String)
}
