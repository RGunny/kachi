package me.rgunny.kachi.story.adapter.outbound.persistence.outbox

import java.time.Instant
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPersistencePort
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxClaim
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

/**
 * outbox 행 저장소 출력 포트의 MongoDB 구현.
 *
 * 소유권을 다투는 claim과 finalize는 조건과 쓰기를 한 명령으로 처리한다.
 */
@Component
class StoryOutboxPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate
) : StoryOutboxPersistencePort {

    override suspend fun save(outbox: StoryOutbox): StoryOutbox {
        return mongoTemplate.save(StoryOutboxMongoDocument.fromDomain(outbox))
            .awaitSingle()
            .toDomain()
    }

    override suspend fun findById(id: StoryOutboxId): StoryOutbox? {
        return mongoTemplate.findById(id.value, StoryOutboxMongoDocument::class.java)
            .awaitSingleOrNull()
            ?.toDomain()
    }

    override suspend fun findPublishable(now: Instant, batchSize: Int): List<StoryOutbox> {
        val query = Query.query(
            Criteria.where(FIELD_STATUS).`is`(StoryOutboxStatus.PENDING.name)
                .and(FIELD_NEXT_RETRY_AT).lte(now)
        ).with(PUBLISH_ORDER).limit(batchSize)

        return findAll(query)
    }

    override suspend fun findStalePublishing(threshold: Instant, batchSize: Int): List<StoryOutbox> {
        val query = Query.query(
            Criteria.where(FIELD_STATUS).`is`(StoryOutboxStatus.PUBLISHING.name)
                .and(FIELD_CLAIMED_AT).lt(threshold)
        ).with(Sort.by(Sort.Direction.ASC, FIELD_CLAIMED_AT)).limit(batchSize)

        return findAll(query)
    }

    override suspend fun findByStatus(status: StoryOutboxStatus, batchSize: Int): List<StoryOutbox> {
        val query = Query.query(
            Criteria.where(FIELD_STATUS).`is`(status.name)
        ).with(PUBLISH_ORDER).limit(batchSize)

        return findAll(query)
    }

    override suspend fun claimPublishing(id: StoryOutboxId, claimedBy: String, now: Instant): StoryOutbox? {
        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(id.value)
                .and(FIELD_STATUS).`is`(StoryOutboxStatus.PENDING.name)
                .and(FIELD_NEXT_RETRY_AT).lte(now)
        )
        val update = Update()
            .set(FIELD_STATUS, StoryOutboxStatus.PUBLISHING.name)
            .set(FIELD_CLAIMED_BY, claimedBy)
            .set(FIELD_CLAIMED_AT, now)
            .set(FIELD_UPDATED_AT, now)

        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            StoryOutboxMongoDocument::class.java
        ).awaitSingleOrNull()?.toDomain()
    }

    /**
     * 확정과 함께 소유권 두 필드를 null로 둔다. 복원은 그것을 소유권 없음으로 읽는다.
     */
    override suspend fun finalize(outbox: StoryOutbox, expectedClaim: StoryOutboxClaim): Boolean {
        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(outbox.id.value)
                .and(FIELD_STATUS).`is`(StoryOutboxStatus.PUBLISHING.name)
                .and(FIELD_CLAIMED_BY).`is`(expectedClaim.claimedBy)
                .and(FIELD_CLAIMED_AT).`is`(expectedClaim.claimedAt)
        )

        return mongoTemplate.updateFirst(query, resultUpdate(outbox), StoryOutboxMongoDocument::class.java)
            .awaitSingle()
            .matchedCount == 1L
    }

    override suspend fun recoverDead(outbox: StoryOutbox): Boolean {
        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(outbox.id.value)
                .and(FIELD_STATUS).`is`(StoryOutboxStatus.DEAD.name)
        )

        return mongoTemplate.findAndModify(query, resultUpdate(outbox), StoryOutboxMongoDocument::class.java)
            .awaitSingleOrNull() != null
    }

    private fun resultUpdate(outbox: StoryOutbox): Update {
        return Update()
            .set(FIELD_STATUS, outbox.status.name)
            .set(FIELD_RETRY_COUNT, outbox.retryCount)
            .set(FIELD_NEXT_RETRY_AT, outbox.nextRetryAt)
            .set(FIELD_LAST_ERROR, outbox.lastError)
            .set(FIELD_PUBLISHED_AT, outbox.publishedAt)
            .set(FIELD_CLAIMED_BY, null)
            .set(FIELD_CLAIMED_AT, null)
            .set(FIELD_UPDATED_AT, outbox.updatedAt)
    }

    private suspend fun findAll(query: Query): List<StoryOutbox> {
        return mongoTemplate.find(query, StoryOutboxMongoDocument::class.java)
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
