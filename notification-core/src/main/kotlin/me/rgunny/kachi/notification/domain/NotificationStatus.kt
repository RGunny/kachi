package me.rgunny.kachi.notification.domain

/**
 * 알림 발송 라이프사이클 상태.
 *
 * 발송 등록 측 (service) 흐름:
 *   REQUESTED ─┬─▶ PUBLISHED
 *              │
 *              └─▶ PUBLISH_FAILED
 *
 * 발송 처리 측 (worker) 흐름:
 *   PUBLISHED ─▶ PROCESSING ─▶ SENT
 *                          └─▶ FAILED ─┬─ 재시도 가능 → RETRY_WAIT ─▶ PROCESSING ...
 *                                      └─ 한도 도달   → DEAD ─▶ DLT 토픽 진입 (운영자 수동 재처리 큐)
 *
 */
enum class NotificationStatus {

    REQUESTED,      // 요청 수신 + 멱등 통과 + 영속 완료
    PUBLISHED,      // Kafka 토픽 발행 성공
    PUBLISH_FAILED, // Kafka 토픽 발행 실패
    PROCESSING,     // worker 진입 + 외부 채널 호출 진행 중
    RETRY_WAIT,     // Kafka 다음 attempt backoff 대기. 직전 단계는 FAILED
    SENT,           // 외부 채널 발송 성공
    FAILED,         // 외부 채널 발송 실패. 다음은 RETRY_WAIT 또는 DEAD
    DEAD            // 자동 재시도 종료. 운영자 수동 재처리만 가능 (DLT 토픽 진입과 함께)
}