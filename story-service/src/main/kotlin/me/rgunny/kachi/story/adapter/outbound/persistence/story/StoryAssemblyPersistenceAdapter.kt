package me.rgunny.kachi.story.adapter.outbound.persistence.story

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.story.adapter.outbound.persistence.outbox.StoryOutboxMongoDocument
import me.rgunny.kachi.story.application.port.outbound.story.StoryAssemblyPersistencePort
import me.rgunny.kachi.story.application.port.outbound.story.model.AttachOutcome
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * 기사 한 건을 story에 붙이는 쓰기를 한 트랜잭션으로 묶는 출력 포트의 MongoDB 구현.
 *
 * 같은 기사의 unique 충돌은 트랜잭션 전체를 되돌리므로 story와 outbox 행도 남지 않는다.
 */
@Component
class StoryAssemblyPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val transactionalOperator: TransactionalOperator
) : StoryAssemblyPersistencePort {

    override suspend fun openStory(story: Story, article: StoryArticle, outbox: StoryOutbox): AttachOutcome {
        requireSameStory(article, story)

        return try {
            transactionalOperator.executeAndAwait {
                mongoTemplate.insert(StoryArticleMongoDocument.fromDomain(article)).awaitSingle()
                mongoTemplate.insert(StoryMongoDocument.fromDomain(story)).awaitSingle()
                mongoTemplate.insert(StoryOutboxMongoDocument.fromDomain(outbox)).awaitSingle()
            }
            AttachOutcome.ATTACHED
        } catch (e: DuplicateKeyException) {
            AttachOutcome.DUPLICATED
        }
    }

    /**
     * 기사 insert, story CAS update, outbox insert 순이다. CAS가 0건이면 트랜잭션을 되돌린다.
     */
    override suspend fun attach(
        article: StoryArticle,
        story: Story,
        expectedVersion: Long,
        outbox: StoryOutbox
    ): AttachOutcome {
        requireSameStory(article, story)

        return try {
            transactionalOperator.executeAndAwait { transaction ->
                mongoTemplate.insert(StoryArticleMongoDocument.fromDomain(article)).awaitSingle()

                val matched = mongoTemplate.updateFirst(
                    StoryMongoDocument.versionQuery(story.id, expectedVersion),
                    StoryMongoDocument.fromDomain(story).toTransitionUpdate(),
                    StoryMongoDocument::class.java
                ).awaitSingle().matchedCount
                if (matched == 0L) {
                    transaction.setRollbackOnly()
                    return@executeAndAwait AttachOutcome.STORY_CHANGED
                }

                mongoTemplate.insert(StoryOutboxMongoDocument.fromDomain(outbox)).awaitSingle()
                AttachOutcome.ATTACHED
            }
        } catch (e: DuplicateKeyException) {
            AttachOutcome.DUPLICATED
        }
    }

    private fun requireSameStory(article: StoryArticle, story: Story) {
        require(article.storyId == story.id) { "다른 story의 기사입니다: article=${article.storyId}, story=${story.id}" }
    }
}
