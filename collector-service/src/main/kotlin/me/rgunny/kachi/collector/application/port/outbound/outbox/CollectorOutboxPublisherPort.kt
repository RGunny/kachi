package me.rgunny.kachi.collector.application.port.outbound.outbox

import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox

/**
 * 기록된 outbox 이벤트를 외부 broker로 내보내는 출력 포트.
 *
 * 발행 성공은 정상 반환으로, 실패는 예외로 알린다.
 * 재시도해도 결과가 달라지지 않는 실패는 [me.rgunny.kachi.collector.application.exception.CollectorOutboxPublishException]에
 * `retryable = false`로 담아 던진다. 그 밖의 예외는 일시 장애로 보고 재시도한다.
 */
interface CollectorOutboxPublisherPort {

    suspend fun publish(outbox: CollectorOutbox)
}
