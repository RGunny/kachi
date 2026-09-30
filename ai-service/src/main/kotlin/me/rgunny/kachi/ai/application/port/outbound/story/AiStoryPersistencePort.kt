package me.rgunny.kachi.ai.application.port.outbound.story

import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.StoryId
import java.time.Instant

/**
 * story 상태 사본 저장소 출력 포트.
 *
 * 갱신은 저장된 version이 기대값일 때만 쓰는 CAS다.
 */
interface AiStoryPersistencePort {

    suspend fun findByStoryId(storyId: StoryId): AiStory?

    suspend fun save(story: AiStory): AiStory

    /**
     * 저장된 version이 [expectedVersion]일 때만 갱신한다.
     */
    suspend fun update(story: AiStory, expectedVersion: Long): Boolean

    /**
     * maxWait를 넘긴 요약 대상 story를 찾는다.
     *
     * 조건은 미요약 기사 1건 이상, 흡수되지 않음, 기준 시각(마지막 버전 시각, 없으면 첫 미요약 기사 시각)이 [threshold] 이전이다.
     */
    suspend fun findSummaryDue(threshold: Instant, limit: Int): List<AiStory>

    /**
     * 흡수된 story와 흡수한 story의 전이, 미요약 기사의 소속 이동을 한 트랜잭션으로 쓴다.
     *
     * 두 story 중 하나라도 CAS에 실패하면 아무것도 쓰지 않고 false를 돌려준다.
     */
    suspend fun merge(
        absorbed: AiStory,
        expectedAbsorbedVersion: Long,
        absorbing: AiStory,
        expectedAbsorbingVersion: Long
    ): Boolean
}
