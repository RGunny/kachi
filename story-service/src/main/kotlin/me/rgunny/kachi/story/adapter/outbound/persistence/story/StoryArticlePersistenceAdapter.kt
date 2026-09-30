package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.time.Instant
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.story.application.port.outbound.story.StoryArticlePersistencePort
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.StoryId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

/**
 * 기사 사본과 소속 저장소 출력 포트의 MongoDB 구현.
 */
@Component
class StoryArticlePersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate
) : StoryArticlePersistencePort {

    override suspend fun findByNewsId(newsId: NewsId): StoryArticle? {
        return mongoTemplate.findById(newsId.value, StoryArticleMongoDocument::class.java)
            .awaitSingleOrNull()
            ?.toDomain()
    }

    override suspend fun findRecentByStory(storyId: StoryId, limit: Int): List<StoryArticle> {
        val query = Query.query(Criteria.where(FIELD_STORY_ID).`is`(storyId.value))
            .with(Sort.by(Sort.Direction.DESC, FIELD_ATTACHED_AT))
            .limit(limit)

        return findAll(query)
    }

    /**
     * story의 기사를 붙은 순으로 전부 읽는다.
     */
    override suspend fun findByStory(storyId: StoryId): List<StoryArticle> {
        val query = Query.query(Criteria.where(FIELD_STORY_ID).`is`(storyId.value))
            .with(Sort.by(Sort.Direction.ASC, FIELD_ATTACHED_AT))

        return findAll(query)
    }

    override suspend fun reassign(from: StoryId, to: StoryId): Long {
        val query = Query.query(Criteria.where(FIELD_STORY_ID).`is`(from.value))
        val update = Update().set(FIELD_STORY_ID, to.value)

        return mongoTemplate.updateMulti(query, update, StoryArticleMongoDocument::class.java)
            .awaitSingle()
            .modifiedCount
    }

    override suspend fun findCollectedAfter(threshold: Instant, after: NewsId?, limit: Int): List<StoryArticle> {
        val criteria = Criteria.where(FIELD_COLLECTED_AT).gte(threshold)
        after?.let { criteria.and(FIELD_ID).gt(it.value) }

        val query = Query.query(criteria)
            .with(Sort.by(Sort.Direction.ASC, FIELD_ID))
            .limit(limit)

        return findAll(query)
    }

    private suspend fun findAll(query: Query): List<StoryArticle> {
        return mongoTemplate.find(query, StoryArticleMongoDocument::class.java)
            .collectList()
            .awaitSingle()
            .map { it.toDomain() }
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_STORY_ID = "storyId"
        const val FIELD_ATTACHED_AT = "attachedAt"
        const val FIELD_COLLECTED_AT = "collectedAt"
    }
}
