package me.rgunny.kachi.story.adapter.outbound.persistence.story

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.story.adapter.outbound.persistence.outbox.StoryOutboxMongoDocument
import me.rgunny.kachi.story.application.port.outbound.story.StoryReorganizePersistencePort
import me.rgunny.kachi.story.application.port.outbound.story.model.ReorganizeOutcome
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * story 재편성 쓰기를 한 트랜잭션으로 묶는 출력 포트의 MongoDB 구현.
 *
 * 같은 병합의 outbox unique 충돌은 트랜잭션 전체를 되돌리므로 story와 기사 소속도 남지 않는다.
 */
@Component
class StoryReorganizePersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val transactionalOperator: TransactionalOperator
) : StoryReorganizePersistencePort {

    /**
     * 두 story CAS update, 기사 reassign, outbox insert 순이다.
     * CAS가 0건이면 트랜잭션을 되돌린다.
     */
    override suspend fun merge(
        target: Story,
        source: Story,
        expectedTargetVersion: Long,
        expectedSourceVersion: Long,
        outbox: StoryOutbox
    ): ReorganizeOutcome {
        require(source.mergedInto == target.id) { "다른 story에 흡수된 source입니다: source=${source.mergedInto}, target=${target.id}" }

        return try {
            transactionalOperator.executeAndAwait { transaction ->
                if (!casUpdate(target, expectedTargetVersion) || !casUpdate(source, expectedSourceVersion)) {
                    transaction.setRollbackOnly()
                    return@executeAndAwait ReorganizeOutcome.STORY_CHANGED
                }

                reassignArticles(from = source.id, to = target.id)
                mongoTemplate.insert(StoryOutboxMongoDocument.fromDomain(outbox)).awaitSingle()
                ReorganizeOutcome.REORGANIZED
            }
        } catch (e: DuplicateKeyException) {
            ReorganizeOutcome.STORY_CHANGED
        }
    }

    /**
     * 원 story CAS update, 새 story insert, 기사 reassign 순이다.
     * CAS가 0건이면 트랜잭션을 되돌린다.
     */
    override suspend fun split(
        original: Story,
        expectedVersion: Long,
        newStory: Story,
        movedNewsIds: List<NewsId>
    ): ReorganizeOutcome {
        require(newStory.parentStoryId == original.id) { "원 story의 후속이 아닌 새 story입니다: parent=${newStory.parentStoryId}, original=${original.id}" }

        return transactionalOperator.executeAndAwait { transaction ->
            if (!casUpdate(original, expectedVersion)) {
                transaction.setRollbackOnly()
                return@executeAndAwait ReorganizeOutcome.STORY_CHANGED
            }

            mongoTemplate.insert(StoryMongoDocument.fromDomain(newStory)).awaitSingle()
            reassignArticles(newsIds = movedNewsIds, to = newStory.id)
            ReorganizeOutcome.REORGANIZED
        }
    }

    private suspend fun casUpdate(story: Story, expectedVersion: Long): Boolean {
        val matched = mongoTemplate.updateFirst(
            StoryMongoDocument.versionQuery(story.id, expectedVersion),
            StoryMongoDocument.fromDomain(story).toTransitionUpdate(),
            StoryMongoDocument::class.java
        ).awaitSingle().matchedCount

        return matched == 1L
    }

    private suspend fun reassignArticles(from: StoryId, to: StoryId) {
        mongoTemplate.updateMulti(
            Query.query(Criteria.where(FIELD_STORY_ID).`is`(from.value)),
            Update().set(FIELD_STORY_ID, to.value),
            StoryArticleMongoDocument::class.java
        ).awaitSingle()
    }

    private suspend fun reassignArticles(newsIds: List<NewsId>, to: StoryId) {
        mongoTemplate.updateMulti(
            Query.query(Criteria.where(FIELD_ID).`in`(newsIds.map { it.value })),
            Update().set(FIELD_STORY_ID, to.value),
            StoryArticleMongoDocument::class.java
        ).awaitSingle()
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_STORY_ID = "storyId"
    }
}
