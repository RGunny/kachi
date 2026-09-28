package me.rgunny.kachi.ai.adapter.outbound.persistence.summary

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.adapter.outbound.persistence.outbox.AiOutboxMongoDocument
import me.rgunny.kachi.ai.adapter.outbound.persistence.story.AiStoryArticleMongoDocument
import me.rgunny.kachi.ai.adapter.outbound.persistence.story.AiStoryMongoDocument
import me.rgunny.kachi.ai.application.port.outbound.summary.StorySummaryPersistencePort
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.domain.summary.StorySummary
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * story 요약 버전 출력 포트의 MongoDB 구현.
 *
 * 버전 저장은 요약 insert, 기사 마킹, story CAS 교체, outbox insert를 한 트랜잭션으로 묶는다.
 * unique 충돌·CAS 실패·마킹 수 불일치 어느 하나라도 나면 전부 되돌린다.
 */
@Component
class StorySummaryPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val transactionalOperator: TransactionalOperator
) : StorySummaryPersistencePort {

    override suspend fun findLatest(storyId: StoryId): StorySummary? {
        return findByStory(storyId, limit = 1).firstOrNull()
    }

    override suspend fun findByStory(storyId: StoryId, limit: Int): List<StorySummary> {
        val query = Query(Criteria.where("storyId").`is`(storyId.value))
            .with(Sort.by(Sort.Direction.DESC, "version"))
            .limit(limit)

        return mongoTemplate.find(query, StorySummaryMongoDocument::class.java)
            .collectList()
            .awaitSingle()
            .map { it.toDomain() }
    }

    override suspend fun saveVersion(
        summary: StorySummary,
        story: AiStory,
        expectedStoryVersion: Long,
        outboxes: List<AiOutbox>
    ): Boolean {
        return try {
            transactionalOperator.executeAndAwait { transaction ->
                mongoTemplate.insert(StorySummaryMongoDocument.fromDomain(summary)).awaitSingle()

                // 1. 이 버전의 기사를 미요약에서 마킹으로 바꾼다. 수가 어긋나면 되돌린다(다른 실행의 개입).
                val marked = mongoTemplate.updateMulti(
                    Query(
                        Criteria.where("_id").`in`(summary.newNewsIds)
                            .and("storyId").`is`(summary.storyId.value)
                            .and("summarizedInVersion").isNull()
                    ),
                    Update.update("summarizedInVersion", summary.version),
                    AiStoryArticleMongoDocument::class.java
                ).awaitSingle().modifiedCount
                if (marked != summary.newNewsIds.size.toLong()) {
                    transaction.setRollbackOnly()
                    return@executeAndAwait false
                }

                // 2. story 상태는 version이 기대값일 때만 바뀐다.
                val replaced = mongoTemplate.findAndReplace(
                    Query(
                        Criteria.where("_id").`is`(story.storyId.value)
                            .and("version").`is`(expectedStoryVersion)
                    ),
                    AiStoryMongoDocument.fromDomain(story)
                ).awaitSingleOrNull()
                if (replaced == null) {
                    transaction.setRollbackOnly()
                    return@executeAndAwait false
                }

                outboxes.forEach { outbox ->
                    mongoTemplate.insert(AiOutboxMongoDocument.fromDomain(outbox)).awaitSingle()
                }

                true
            }
        } catch (error: DuplicateKeyException) {
            // (storyId, version) unique 또는 outbox eventKey unique 충돌(같은 버전을 먼저 저장한 실행 있음)
            false
        }
    }
}
