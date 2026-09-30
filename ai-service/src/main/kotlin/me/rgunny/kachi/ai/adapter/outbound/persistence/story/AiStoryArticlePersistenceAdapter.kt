package me.rgunny.kachi.ai.adapter.outbound.persistence.story

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.application.port.outbound.story.AiStoryArticlePersistencePort
import me.rgunny.kachi.ai.application.port.outbound.story.model.RecordStoryArticleOutcome
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.AiStoryArticle
import me.rgunny.kachi.ai.domain.story.StoryId
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * 기사 사본 출력 포트의 MongoDB 구현.
 *
 * 기사 insert와 story 상태 쓰기를 한 트랜잭션으로 묶는다.
 * newsId(_id) 충돌은 재전달 복구로, story version 어긋남은 경합으로 구분해 돌려준다.
 */
@Component
class AiStoryArticlePersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val transactionalOperator: TransactionalOperator
) : AiStoryArticlePersistencePort {

    override suspend fun openStory(story: AiStory, article: AiStoryArticle): RecordStoryArticleOutcome {
        return try {
            transactionalOperator.executeAndAwait {
                mongoTemplate.insert(AiStoryArticleMongoDocument.fromDomain(article)).awaitSingle()
                mongoTemplate.insert(AiStoryMongoDocument.fromDomain(story)).awaitSingle()
            }

            RecordStoryArticleOutcome.RECORDED
        } catch (error: DuplicateKeyException) {
            duplicatedOutcome(article)
        }
    }

    override suspend fun attach(
        article: AiStoryArticle,
        story: AiStory,
        expectedVersion: Long
    ): RecordStoryArticleOutcome {
        return try {
            transactionalOperator.executeAndAwait { transaction ->
                mongoTemplate.insert(AiStoryArticleMongoDocument.fromDomain(article)).awaitSingle()
                val replaced = mongoTemplate.findAndReplace(
                    Query(
                        Criteria.where("_id").`is`(story.storyId.value)
                            .and("version").`is`(expectedVersion)
                    ),
                    AiStoryMongoDocument.fromDomain(story)
                ).awaitSingleOrNull()

                if (replaced == null) {
                    transaction.setRollbackOnly()
                    RecordStoryArticleOutcome.STORY_CHANGED
                } else {
                    RecordStoryArticleOutcome.RECORDED
                }
            }
        } catch (error: DuplicateKeyException) {
            duplicatedOutcome(article)
        }
    }

    override suspend fun findPendingByStory(storyId: StoryId, limit: Int): List<AiStoryArticle> {
        val query = Query(
            Criteria.where("storyId").`is`(storyId.value)
                .and("summarizedInVersion").isNull()
        )
            .with(Sort.by(Sort.Direction.ASC, "attachedAt"))
            .limit(limit)

        return mongoTemplate.find(query, AiStoryArticleMongoDocument::class.java)
            .collectList()
            .awaitSingle()
            .map { it.toDomain() }
    }

    /**
     * _id 충돌(기사 또는 story)을 기사의 존재로 [RecordStoryArticleOutcome.DUPLICATED]와 [RecordStoryArticleOutcome.STORY_CHANGED]로 가른다.
     */
    private suspend fun duplicatedOutcome(article: AiStoryArticle): RecordStoryArticleOutcome {
        val exists = mongoTemplate.findById(article.newsId, AiStoryArticleMongoDocument::class.java)
            .awaitSingleOrNull() != null

        return if (exists) RecordStoryArticleOutcome.DUPLICATED else RecordStoryArticleOutcome.STORY_CHANGED
    }
}
