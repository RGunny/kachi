package me.rgunny.kachi.ai.domain.story

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import java.time.Instant

/**
 * ai-service가 요약 대상으로 추적하는 story의 상태.
 *
 * 키워드와 기사 수는 이벤트가 실어 온 스냅샷의 합집합·최댓값으로만 자란다.
 * 전이마다 [version]이 1씩 커진다(저장의 CAS 기대값).
 */
class AiStory private constructor(
    val storyId: StoryId,
    val keywords: List<AiKeyword>,
    val articleCount: Int,
    /** 마지막으로 만든 요약 버전(요약이 없으면 0). */
    val latestVersion: Long,
    val latestVersionAt: Instant?,
    val pendingCount: Int,
    val oldestPendingAt: Instant?,
    /** 이 story를 흡수한 story(흡수되지 않았으면 null). */
    val mergedInto: StoryId?,
    val version: Long,
    val updatedAt: Instant
) {

    /** 다음 요약이 받을 버전 번호. */
    val nextVersion: Long
        get() = latestVersion + 1

    val merged: Boolean
        get() = mergedInto != null

    /**
     * maxWait 판정의 기준 시각.
     *
     * 요약이 있으면 마지막 버전 시각, 없으면 첫 미요약 기사의 attachedAt이다.
     */
    val summaryWaitBaseline: Instant?
        get() = latestVersionAt ?: oldestPendingAt

    /**
     * 기사 스냅샷 하나를 받아 미요약 수를 올린다.
     */
    fun accept(
        keywords: List<AiKeyword>,
        articleCount: Int,
        attachedAt: Instant,
        now: Instant
    ): AiStory {
        require(!merged) { "흡수된 story는 기사를 받을 수 없습니다: ${storyId.value}" }

        return copy(
            keywords = union(this.keywords, keywords),
            articleCount = maxOf(this.articleCount, articleCount),
            pendingCount = pendingCount + 1,
            oldestPendingAt = minOfNullable(oldestPendingAt, attachedAt),
            updatedAt = now
        )
    }

    /**
     * 요약 한 버전이 만들어진 뒤의 상태.
     *
     * [remainingOldestPendingAt]은 이번 버전에 실리지 않고 남은 미요약 기사 중 가장 오래된 attachedAt이다.
     * 남은 기사가 없으면 null이어야 한다.
     */
    fun summarized(
        summarizedCount: Int,
        remainingOldestPendingAt: Instant?,
        now: Instant
    ): AiStory {
        require(summarizedCount in 1..pendingCount) {
            "요약된 기사 수가 미요약 수를 벗어납니다: summarized=$summarizedCount, pending=$pendingCount"
        }
        val remaining = pendingCount - summarizedCount
        require((remaining == 0) == (remainingOldestPendingAt == null)) {
            "남은 미요약 기사 수와 기준 시각이 맞지 않습니다: remaining=$remaining"
        }

        return copy(
            latestVersion = nextVersion,
            latestVersionAt = now,
            pendingCount = remaining,
            oldestPendingAt = remainingOldestPendingAt,
            updatedAt = now
        )
    }

    /**
     * 이 story가 [target]에 흡수된 것으로 표시한다.
     *
     * 미요약 기사는 흡수한 쪽으로 옮겨진 뒤여야 한다.
     */
    fun mergeInto(target: StoryId, now: Instant): AiStory {
        require(!merged) { "이미 흡수된 story입니다: ${storyId.value}" }
        require(target != storyId) { "story는 자신에게 흡수될 수 없습니다: ${storyId.value}" }

        return copy(
            pendingCount = 0,
            oldestPendingAt = null,
            mergedInto = target,
            updatedAt = now
        )
    }

    /**
     * 흡수된 story의 미요약 기사와 키워드를 넘겨받는다.
     */
    fun absorb(
        movedPendingCount: Int,
        movedOldestPendingAt: Instant?,
        mergedKeywords: List<AiKeyword>,
        now: Instant
    ): AiStory {
        require(!merged) { "흡수된 story는 다른 story를 받을 수 없습니다: ${storyId.value}" }
        require(movedPendingCount >= 0) { "옮겨 온 미요약 기사 수는 음수일 수 없습니다: $movedPendingCount" }
        require(movedPendingCount == 0 || movedOldestPendingAt != null) {
            "옮겨 온 미요약 기사가 있으면 기준 시각이 필요합니다"
        }

        return copy(
            keywords = union(keywords, mergedKeywords),
            pendingCount = pendingCount + movedPendingCount,
            oldestPendingAt = movedOldestPendingAt?.let { minOfNullable(oldestPendingAt, it) } ?: oldestPendingAt,
            updatedAt = now
        )
    }

    private fun copy(
        keywords: List<AiKeyword> = this.keywords,
        articleCount: Int = this.articleCount,
        latestVersion: Long = this.latestVersion,
        latestVersionAt: Instant? = this.latestVersionAt,
        pendingCount: Int = this.pendingCount,
        oldestPendingAt: Instant? = this.oldestPendingAt,
        mergedInto: StoryId? = this.mergedInto,
        updatedAt: Instant
    ): AiStory {
        return AiStory(
            storyId = storyId,
            keywords = keywords,
            articleCount = articleCount,
            latestVersion = latestVersion,
            latestVersionAt = latestVersionAt,
            pendingCount = pendingCount,
            oldestPendingAt = oldestPendingAt,
            mergedInto = mergedInto,
            version = version + 1,
            updatedAt = updatedAt
        )
    }

    companion object {

        fun open(
            storyId: StoryId,
            keywords: List<AiKeyword>,
            articleCount: Int,
            attachedAt: Instant,
            now: Instant
        ): AiStory {
            require(keywords.isNotEmpty()) { "story 키워드는 하나 이상이어야 합니다" }
            require(articleCount >= 1) { "story 기사 수는 1 이상이어야 합니다: $articleCount" }

            return AiStory(
                storyId = storyId,
                keywords = keywords.distinct(),
                articleCount = articleCount,
                latestVersion = 0,
                latestVersionAt = null,
                pendingCount = 1,
                oldestPendingAt = attachedAt,
                mergedInto = null,
                version = 1,
                updatedAt = now
            )
        }

        /**
         * 기사 사본 없이 흡수 사실만 기록하는 자리표시(attached 이벤트가 병합 이벤트보다 늦게 도착하는 경우).
         *
         * 키워드는 비어 있고 기사 수와 미요약 수는 0이다.
         */
        fun trackMerged(
            storyId: StoryId,
            mergedInto: StoryId,
            now: Instant
        ): AiStory {
            require(mergedInto != storyId) { "story는 자신에게 흡수될 수 없습니다: ${storyId.value}" }

            return AiStory(
                storyId = storyId,
                keywords = emptyList(),
                articleCount = 0,
                latestVersion = 0,
                latestVersionAt = null,
                pendingCount = 0,
                oldestPendingAt = null,
                mergedInto = mergedInto,
                version = 1,
                updatedAt = now
            )
        }

        fun restore(
            storyId: StoryId,
            keywords: List<AiKeyword>,
            articleCount: Int,
            latestVersion: Long,
            latestVersionAt: Instant?,
            pendingCount: Int,
            oldestPendingAt: Instant?,
            mergedInto: StoryId?,
            version: Long,
            updatedAt: Instant
        ): AiStory {
            return AiStory(
                storyId = storyId,
                keywords = keywords,
                articleCount = articleCount,
                latestVersion = latestVersion,
                latestVersionAt = latestVersionAt,
                pendingCount = pendingCount,
                oldestPendingAt = oldestPendingAt,
                mergedInto = mergedInto,
                version = version,
                updatedAt = updatedAt
            )
        }

        private fun union(
            base: List<AiKeyword>,
            added: List<AiKeyword>
        ): List<AiKeyword> {
            return (base + added).distinct()
        }

        private fun minOfNullable(current: Instant?, candidate: Instant): Instant {
            return if (current == null || candidate.isBefore(current)) candidate else current
        }
    }
}
