package me.rgunny.kachi.ai.adapter.outbound.persistence.story

import java.time.Instant
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.application.port.outbound.story.AiStoryPersistencePort
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.StoryId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * story 상태 사본 출력 포트의 MongoDB 구현.
 *
 * 갱신은 _id와 version을 함께 조건으로 거는 교체다. version이 다르면 아무것도 바꾸지 않는다.
 */
@Component
class AiStoryPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val transactionalOperator: TransactionalOperator
) : AiStoryPersistencePort {

    override suspend fun findByStoryId(storyId: StoryId): AiStory? {
        return mongoTemplate.findById(storyId.value, AiStoryMongoDocument::class.java)
            .awaitSingleOrNull()
            ?.toDomain()
    }

    override suspend fun save(story: AiStory): AiStory {
        return mongoTemplate.insert(AiStoryMongoDocument.fromDomain(story))
            .awaitSingle()
            .toDomain()
    }

    override suspend fun update(story: AiStory, expectedVersion: Long): Boolean {
        return mongoTemplate.findAndReplace(
            casQuery(story.storyId, expectedVersion),
            AiStoryMongoDocument.fromDomain(story)
        ).awaitSingleOrNull() != null
    }

    override suspend fun findSummaryDue(threshold: Instant, limit: Int): List<AiStory> {
        val criteria = Criteria.where("pendingCount").gte(1)
            .and("mergedInto").isNull()
            .orOperator(
                Criteria.where("latestVersionAt").lte(threshold),
                Criteria().andOperator(
                    Criteria.where("latestVersionAt").isNull(),
                    Criteria.where("oldestPendingAt").lte(threshold)
                )
            )
        val query = Query(criteria)
            .with(Sort.by(Sort.Direction.ASC, "oldestPendingAt"))
            .limit(limit)

        return mongoTemplate.find(query, AiStoryMongoDocument::class.java)
            .collectList()
            .awaitSingle()
            .map { it.toDomain() }
    }

    /**
     * 두 story의 CAS 교체와 미요약 기사의 소속 이동을 한 트랜잭션으로 쓴다.
     *
     * 어느 한쪽의 version이 어긋나면 전부 되돌리고 false를 돌려준다.
     */
    override suspend fun merge(
        absorbed: AiStory,
        expectedAbsorbedVersion: Long,
        absorbing: AiStory,
        expectedAbsorbingVersion: Long
    ): Boolean {
        return transactionalOperator.executeAndAwait { transaction ->
            val absorbedReplaced = mongoTemplate.findAndReplace(
                casQuery(absorbed.storyId, expectedAbsorbedVersion),
                AiStoryMongoDocument.fromDomain(absorbed)
            ).awaitSingleOrNull()
            val absorbingReplaced = mongoTemplate.findAndReplace(
                casQuery(absorbing.storyId, expectedAbsorbingVersion),
                AiStoryMongoDocument.fromDomain(absorbing)
            ).awaitSingleOrNull()

            if (absorbedReplaced == null || absorbingReplaced == null) {
                transaction.setRollbackOnly()
                return@executeAndAwait false
            }

            mongoTemplate.updateMulti(
                Query(
                    Criteria.where("storyId").`is`(absorbed.storyId.value)
                        .and("summarizedInVersion").isNull()
                ),
                Update.update("storyId", absorbing.storyId.value),
                AiStoryArticleMongoDocument::class.java
            ).awaitSingle()

            true
        }
    }

    private fun casQuery(storyId: StoryId, expectedVersion: Long): Query {
        return Query(
            Criteria.where("_id").`is`(storyId.value)
                .and("version").`is`(expectedVersion)
        )
    }
}
