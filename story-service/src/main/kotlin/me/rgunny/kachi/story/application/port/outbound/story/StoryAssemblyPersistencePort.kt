package me.rgunny.kachi.story.application.port.outbound.story

import me.rgunny.kachi.story.application.port.outbound.story.model.AttachOutcome
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.outbox.StoryOutbox

/**
 * 기사 한 건을 story에 붙인 결과를 한 트랜잭션에 쓰는 출력 포트.
 *
 * 기사 사본, story 상태, outbox 행은 함께 남거나 함께 남지 않는다. replica set 전제다(ADR 017).
 */
interface StoryAssemblyPersistencePort {

    /** 새 story를 첫 기사와 함께 만든다. 같은 기사가 이미 있으면 [AttachOutcome.DUPLICATED]다. */
    suspend fun openStory(story: Story, article: StoryArticle, outbox: StoryOutbox): AttachOutcome

    /**
     * 있는 story에 기사를 붙인다. 저장된 version이 [expectedVersion]과 다르면 [AttachOutcome.STORY_CHANGED]다.
     */
    suspend fun attach(article: StoryArticle, story: Story, expectedVersion: Long, outbox: StoryOutbox): AttachOutcome
}
