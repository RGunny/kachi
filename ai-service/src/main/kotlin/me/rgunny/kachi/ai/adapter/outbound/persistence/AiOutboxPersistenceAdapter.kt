package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.application.port.outbound.persistence.AiOutboxPersistencePort
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.outbox.AiOutboxClaim
import me.rgunny.kachi.ai.domain.outbox.AiOutboxId
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * outbox 저장소 출력 포트의 MongoDB 구현.
 *
 * 소유권을 다투는 두 연산(claim, finalize)이 조건과 쓰기를 한 번의 명령으로 처리해야 해서 repository가 아니라 template을 쓴다.
 */
@Component
class AiOutboxPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate
) : AiOutboxPersistencePort {

    override suspend fun save(outbox: AiOutbox): AiOutbox {
        return mongoTemplate.save(AiOutboxMongoDocument.fromDomain(outbox))
            .awaitSingle()
            .toDomain()
    }

    override suspend fun findById(id: AiOutboxId): AiOutbox? {
        return mongoTemplate.findById(id.value, AiOutboxMongoDocument::class.java)
            .awaitSingleOrNull()
            ?.toDomain()
    }

    override suspend fun findPublishable(now: Instant, batchSize: Int): List<AiOutbox> {
        val query = Query.query(
            Criteria.where(FIELD_STATUS).`is`(AiOutboxStatus.PENDING.name)
                .and(FIELD_NEXT_RETRY_AT).lte(now)
        ).with(PUBLISH_ORDER).limit(batchSize)

        return findAll(query)
    }

    override suspend fun findStalePublishing(threshold: Instant, batchSize: Int): List<AiOutbox> {
        val query = Query.query(
            Criteria.where(FIELD_STATUS).`is`(AiOutboxStatus.PUBLISHING.name)
                .and(FIELD_CLAIMED_AT).lt(threshold)
        ).with(Sort.by(Sort.Direction.ASC, FIELD_CLAIMED_AT)).limit(batchSize)

        return findAll(query)
    }

    override suspend fun findByStatus(status: AiOutboxStatus, batchSize: Int): List<AiOutbox> {
        val query = Query.query(
            Criteria.where(FIELD_STATUS).`is`(status.name)
        ).with(PUBLISH_ORDER).limit(batchSize)

        return findAll(query)
    }

    override suspend fun claimPublishing(id: AiOutboxId, claimedBy: String, now: Instant): AiOutbox? {
        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(id.value)
                .and(FIELD_STATUS).`is`(AiOutboxStatus.PENDING.name)
                .and(FIELD_NEXT_RETRY_AT).lte(now)
        )
        val update = Update()
            .set(FIELD_STATUS, AiOutboxStatus.PUBLISHING.name)
            .set(FIELD_CLAIMED_BY, claimedBy)
            .set(FIELD_CLAIMED_AT, now)
            .set(FIELD_UPDATED_AT, now)

        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            AiOutboxMongoDocument::class.java
        ).awaitSingleOrNull()?.toDomain()
    }

    override suspend fun finalize(outbox: AiOutbox, expectedClaim: AiOutboxClaim): Boolean {
        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(outbox.id.value)
                .and(FIELD_STATUS).`is`(AiOutboxStatus.PUBLISHING.name)
                .and(FIELD_CLAIMED_BY).`is`(expectedClaim.claimedBy)
                .and(FIELD_CLAIMED_AT).`is`(expectedClaim.claimedAt)
        )
        // 확정과 함께 소유권을 비운다. null을 넣는 것은 필드를 지우는 것이 아니라 null로 두는 것이고, 복원은 그것을 소유권 없음으로 읽는다.
        val update = Update()
            .set(FIELD_STATUS, outbox.status.name)
            .set(FIELD_RETRY_COUNT, outbox.retryCount)
            .set(FIELD_NEXT_RETRY_AT, outbox.nextRetryAt)
            .set(FIELD_LAST_ERROR, outbox.lastError)
            .set(FIELD_PUBLISHED_AT, outbox.publishedAt)
            .set(FIELD_CLAIMED_BY, null)
            .set(FIELD_CLAIMED_AT, null)
            .set(FIELD_UPDATED_AT, outbox.updatedAt)

        return mongoTemplate.updateFirst(query, update, AiOutboxMongoDocument::class.java)
            .awaitSingle()
            .matchedCount == 1L
    }

    /**
     * 복구 결과를 DEAD 행에만 쓴다.
     * 조건에 걸리지 않으면 반환 문서가 없고, 그것이 곧 복구 실패다.
     */
    override suspend fun recoverDead(outbox: AiOutbox): Boolean {
        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(outbox.id.value)
                .and(FIELD_STATUS).`is`(AiOutboxStatus.DEAD.name)
        )
        val update = Update()
            .set(FIELD_STATUS, outbox.status.name)
            .set(FIELD_RETRY_COUNT, outbox.retryCount)
            .set(FIELD_NEXT_RETRY_AT, outbox.nextRetryAt)
            .set(FIELD_LAST_ERROR, outbox.lastError)
            .set(FIELD_PUBLISHED_AT, outbox.publishedAt)
            .set(FIELD_CLAIMED_BY, null)
            .set(FIELD_CLAIMED_AT, null)
            .set(FIELD_UPDATED_AT, outbox.updatedAt)

        return mongoTemplate.findAndModify(
            query,
            update,
            AiOutboxMongoDocument::class.java
        ).awaitSingleOrNull() != null
    }

    private suspend fun findAll(query: Query): List<AiOutbox> {
        return mongoTemplate.find(query, AiOutboxMongoDocument::class.java)
            .collectList()
            .awaitSingle()
            .map { it.toDomain() }
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_STATUS = "status"
        const val FIELD_RETRY_COUNT = "retryCount"
        const val FIELD_NEXT_RETRY_AT = "nextRetryAt"
        const val FIELD_LAST_ERROR = "lastError"
        const val FIELD_PUBLISHED_AT = "publishedAt"
        const val FIELD_CLAIMED_BY = "claimedBy"
        const val FIELD_CLAIMED_AT = "claimedAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_CREATED_AT = "createdAt"

        // 오래 기다린 행이 먼저 나간다. 같은 시각이면 먼저 만들어진 행이 앞선다.
        val PUBLISH_ORDER: Sort = Sort.by(Sort.Direction.ASC, FIELD_NEXT_RETRY_AT, FIELD_CREATED_AT)
    }
}
