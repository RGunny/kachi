package me.rgunny.kachi.notification.domain

/**
 * 알림 outbox 발행 상태.
 *
 * PENDING ─▶ PUBLISHING ─┬─▶ PUBLISHED  (Kafka broker ack 수신, terminal)
 *                        ├─▶ PENDING    (publish 실패 시 retry 가능)
 *                        └─▶ DEAD       (재시도 한도 초과 또는 즉시 DEAD)
 *
 * DEAD ─▶ PENDING (운영자 수동 복구)
 *
 * claim 은 동시 발행을 막는 1차 방어선이다.
 * 다만 publish 성공 후 DB 반영 전에 timeout 회수가 발생하면 같은 이벤트가 다시 발행될 수 있으므로,
 * consumer 에서 notificationId/channel 기준 멱등 처리를 2차 방어선으로 둔다.
 */
enum class NotificationOutboxStatus {

    PENDING,     // 발행 대기 (또는 재시도 대기)
    PUBLISHING,  // 처리 권한 획득 - CAS claim 후 publish 진행 중
    PUBLISHED,   // Kafka 발행 성공
    DEAD         // 재시도 한도 초과 또는 즉시 DEAD
    ;
}


