package me.rgunny.kachi.ai.application.port.outbound.summary

import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.domain.summary.StorySummary

/**
 * story 요약 버전 저장소 출력 포트.
 *
 * (storyId, version) unique index가 버전 하나당 문서 하나 계약을 보장한다.
 */
interface StorySummaryPersistencePort {

    suspend fun findLatest(storyId: StoryId): StorySummary?

    /**
     * story의 요약 버전을 최신 순으로 읽는다.
     */
    suspend fun findByStory(storyId: StoryId, limit: Int): List<StorySummary>

    /**
     * 요약 버전, 반영 기사 마킹, story 상태 CAS, 발행 대기 이벤트를 한 트랜잭션으로 쓴다.
     *
     * (storyId, version) unique 충돌·story CAS 실패·마킹 수 불일치면 아무것도 쓰지 않고 false를 돌려준다.
     * false는 다른 실행이 같은 버전을 먼저 저장했다는 뜻이다.
     */
    suspend fun saveVersion(
        summary: StorySummary,
        story: AiStory,
        expectedStoryVersion: Long,
        outboxes: List<AiOutbox>
    ): Boolean
}
