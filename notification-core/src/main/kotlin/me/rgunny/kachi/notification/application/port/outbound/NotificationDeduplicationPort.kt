package me.rgunny.kachi.notification.application.port.outbound

import java.time.Duration

/**
 * 빠른 중복 처리 차단 port.
 *
 * Redis SETNX 같은 저장소를 통해 같은 요청/dispatch가 동시에 처리되는 것을 1차로 차단한다.
 * 최종 정합성은 DB unique key와 도메인 상태 전이 가드가 함께 보장한다.
 */
interface NotificationDeduplicationPort {

    suspend fun markIfAbsent(key: String, ttl: Duration): Boolean

    suspend fun release(key: String)
}
