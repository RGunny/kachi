package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.time.Instant
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.story.application.port.outbound.story.StoryPersistencePort
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryStatus
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

/**
 * story 저장소 출력 포트의 MongoDB 구현.
 */
@Component
class StoryPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate
) : StoryPersistencePort {

    override suspend fun save(story: Story): Story {
        return mongoTemplate.save(StoryMongoDocument.fromDomain(story))
            .awaitSingle()
            .toDomain()
    }

    override suspend fun findById(id: StoryId): Story? {
        return mongoTemplate.findById(id.value, StoryMongoDocument::class.java)
            .awaitSingleOrNull()
            ?.toDomain()
    }

    override suspend fun findByIds(ids: Collection<StoryId>): List<Story> {
        if (ids.isEmpty()) return emptyList()

        val query = Query.query(
            Criteria.where(StoryMongoDocument.FIELD_ID).`in`(ids.map { it.value })
        )

        return findAll(query)
    }

    /**
     * 저장된 version이 [expectedVersion]일 때만 전이 결과를 쓴다.
     */
    override suspend fun update(story: Story, expectedVersion: Long): Boolean {
        val query = StoryMongoDocument.versionQuery(story.id, expectedVersion)
        val update = StoryMongoDocument.fromDomain(story).toTransitionUpdate()

        return mongoTemplate.updateFirst(query, update, StoryMongoDocument::class.java)
            .awaitSingle()
            .matchedCount == 1L
    }

    override suspend fun findOpenWithLastArticleBefore(threshold: Instant, limit: Int): List<Story> {
        val query = Query.query(
            Criteria.where(StoryMongoDocument.FIELD_STATUS).`is`(StoryStatus.OPEN.name)
                .and(StoryMongoDocument.FIELD_LAST_ARTICLE_AT).lt(threshold)
        ).with(Sort.by(Sort.Direction.ASC, StoryMongoDocument.FIELD_LAST_ARTICLE_AT)).limit(limit)

        return findAll(query)
    }

    override suspend fun find(status: StoryStatus?, openedAfter: Instant?, limit: Int): List<Story> {
        val criteria = mutableListOf<Criteria>()
        status?.let { criteria.add(Criteria.where(StoryMongoDocument.FIELD_STATUS).`is`(it.name)) }
        openedAfter?.let { criteria.add(Criteria.where(StoryMongoDocument.FIELD_OPENED_AT).gte(it)) }

        val query = Query(if (criteria.isEmpty()) Criteria() else Criteria().andOperator(criteria))
            .with(Sort.by(Sort.Direction.DESC, StoryMongoDocument.FIELD_OPENED_AT))
            .limit(limit)

        return findAll(query)
    }

    private suspend fun findAll(query: Query): List<Story> {
        return mongoTemplate.find(query, StoryMongoDocument::class.java)
            .collectList()
            .awaitSingle()
            .map { it.toDomain() }
    }
}
