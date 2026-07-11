package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.application.port.outbound.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Repository

/**
 * 외부 채널 발송 결과 저장 MongoDB 구현.
 *
 * 이 adapter가 worker dispatch 흐름의 DB finalize 경계다.
 *
 * 전체 dispatch 흐름은 다음처럼 분리한다.
 *
 * 1. Redis dedupe가 같은 Kafka message의 동시 처리를 1차로 막는다.
 * 2. NotificationPersistencePort.claimFromPublished/claimFromRetryWait가 PROCESSING 상태를 CAS로 선점한다.
 * 3. Redis idempotency key를 조회/생성하고 vendor API를 호출한다.
 * 4. vendor 호출 결과만 이 adapter가 MongoDB에 최종 저장한다.
 *
 * MongoDB transaction은 Redis dedupe, Redis idempotency key, vendor HTTP 호출을 rollback하지 못한다.
 * 현재 Notification은 단일 document aggregate이므로 최종 상태 저장은 MongoDB 단일 document 원자 write로 충분하다.
 * 나중에 dispatch outbox/history 별도 collection처럼 함께 확정해야 하는 document가 생기면 이 adapter가 transaction 경계가 된다.
 */
@Repository
class NotificationMongoDispatchPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mapper: NotificationDocumentMapper,
) : NotificationDispatchPersistencePort {

    /**
     * vendor 호출 이후 core가 확정한 SENT/RETRY_WAIT/DEAD 상태를 저장한다.
     */
    override suspend fun saveFinalized(notification: Notification): Notification {
        // 1. core에서 계산한 최종 상태와 history를 Mongo document로 변환한다.
        return mongoTemplate.save(mapper.toDocument(notification))
            // 2. 단일 Notification aggregate 저장은 MongoDB 단일 document 원자성에 맡긴다.
            .map(mapper::toDomain)
            .awaitSingle()
    }
}
