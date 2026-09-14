package me.rgunny.kachi.story.application.port.outbound.story

import me.rgunny.kachi.story.application.port.outbound.story.model.ReorganizeOutcome
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.outbox.StoryOutbox

/**
 * story 사이의 기사 재편성을 한 트랜잭션에 쓰는 출력 포트.
 *
 * 두 story 상태, 기사 소속, outbox 행은 함께 남거나 함께 남지 않는다. replica set 전제다(ADR 017).
 */
interface StoryReorganizePersistencePort {

    /**
     * [source]의 기사 전부를 [target]으로 옮기고 두 story와 outbox 행을 쓴다.
     *
     * 저장된 version이 [expectedTargetVersion]·[expectedSourceVersion]과 하나라도 다르면 [ReorganizeOutcome.STORY_CHANGED]다.
     */
    suspend fun merge(
        target: Story,
        source: Story,
        expectedTargetVersion: Long,
        expectedSourceVersion: Long,
        outbox: StoryOutbox
    ): ReorganizeOutcome
}
